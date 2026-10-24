package com.tutorbase.rewards;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the rewards interface: callers may read their balance/collection, inspect the
 * server-owned card catalog, and draw cards. Active card data is read from the database
 * on every request; nothing about sets/cards/UP rotation is hardcoded here.
 */
@Service
class RewardService {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;
    private final RandomGenerator random;

    @Autowired
    RewardService(JdbcClient jdbc, Clock clock, ZoneId zone) {
        // ThreadLocalRandom: 不依赖 RandomGeneratorFactory 的提供者查找，
        // 兼容 Temurin JRE 镜像（该镜像缺少 L32X64MixRandom 提供者，getDefault() 会启动失败），
        // 且线程安全，适合并发抽卡请求。
        this(jdbc, clock, zone, ThreadLocalRandom.current());
    }

    /** Test seam: inject a deterministic random source. */
    RewardService(JdbcClient jdbc, Clock clock, ZoneId zone, RandomGenerator random) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
        this.random = random;
    }

    RewardSummary summary(long learnerId) {
        PointsRow points = pointsRow(learnerId);
        List<CardReward> cards = jdbc.sql("""
                SELECT id, card_key, set_key, rarity, claimed_at
                FROM public.card_collection
                WHERE student_id = :studentId
                ORDER BY claimed_at DESC, id DESC
                """)
                .param("studentId", learnerId)
                .query(RewardService::mapCard)
                .list();
        return new RewardSummary(points.points(), points.lifetimePoints(), cards,
                collectionStats(learnerId, points.drawCount()));
    }

    /** 卡池目录：当前 UP 卡、全部启用卡组、概率与消耗；目录来自数据库。 */
    CatalogResult catalog() {
        RewardCatalog.Catalog catalog = loadCatalog();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        int isoWeek = RewardCatalog.isoWeek(now);
        RewardCatalog.CatalogSet upSet = catalog.upSet(isoWeek);
        UpCard currentUp = toUpCard(upSet, now);
        List<CatalogSetView> sets = catalog.sets().stream()
                .map(RewardService::toCatalogSetView)
                .toList();
        return new CatalogResult(RewardCatalog.DRAW_COST, RewardCatalog.RARE_RATE,
                RewardCatalog.UP_RATE_WHEN_RARE, currentUp, sets,
                catalog.totalCards(), catalog.totalSets());
    }

    @Transactional
    DrawResult draw(long learnerId, int count) {
        RewardCatalog.Catalog catalog = loadCatalog();
        if (catalog.sets().isEmpty() || catalog.rareCards().isEmpty() || catalog.commonCards().isEmpty()) {
            throw new IllegalStateException("card catalog has no active cards");
        }
        jdbc.sql("""
                INSERT INTO public.user_points (student_id, points, updated_at)
                VALUES (:studentId, 0, now())
                ON CONFLICT (student_id) DO NOTHING
                """).param("studentId", learnerId).update();
        PointsRow locked = jdbc.sql("""
                SELECT points, lifetime_points, draw_count
                FROM public.user_points
                WHERE student_id = :studentId
                FOR UPDATE
                """).param("studentId", learnerId).query(RewardService::mapPoints).single();
        long cost = RewardCatalog.DRAW_COST * count;
        if (locked.points() < cost) {
            throw new RewardInsufficientPoints();
        }

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        int isoWeek = RewardCatalog.isoWeek(now);
        RewardCatalog.CatalogSet upSet = catalog.upSet(isoWeek);
        RewardCatalog.CardDefinition upCard = upSet.rare();
        UpCard currentUp = toUpCard(upSet, now);

        List<DrawnCard> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RewardCatalog.CardDefinition definition = drawOne(catalog, upCard);
            CardReward card = jdbc.sql("""
                    INSERT INTO public.card_collection
                        (student_id, card_key, set_key, rarity)
                    VALUES (:studentId, :cardKey, :setKey, :rarity)
                        RETURNING id, card_key, set_key, rarity, claimed_at
                    """).param("studentId", learnerId)
                    .param("cardKey", definition.cardKey())
                    .param("setKey", definition.setKey())
                    .param("rarity", definition.rarity())
                    .query(RewardService::mapCard)
                    .single();
            cards.add(new DrawnCard(card.id(), card.cardKey(), card.setKey(), card.rarity(),
                    card.claimedAt(), definition.cardKey().equals(upCard.cardKey())));
        }

        PointsRow updated = jdbc.sql("""
                UPDATE public.user_points
                SET points = :points, draw_count = draw_count + :count, updated_at = now()
                WHERE student_id = :studentId
                RETURNING points, lifetime_points, draw_count
                """).param("points", locked.points() - cost)
                .param("count", count)
                .param("studentId", learnerId)
                .query(RewardService::mapPoints)
                .single();
        return new DrawResult(updated.points(), updated.lifetimePoints(), currentUp, cards,
                collectionStats(learnerId, updated.drawCount()));
    }

    private RewardCatalog.CardDefinition drawOne(RewardCatalog.Catalog catalog,
                                                 RewardCatalog.CardDefinition upCard) {
        if (random.nextDouble() < RewardCatalog.RARE_RATE) {
            List<RewardCatalog.CardDefinition> rareCards = catalog.rareCards();
            boolean upRoll = upCard != null && random.nextDouble() < RewardCatalog.UP_RATE_WHEN_RARE;
            return RewardCatalog.chooseRare(rareCards, upCard, upRoll, random.nextInt(rareCards.size()));
        }
        List<RewardCatalog.CardDefinition> commonCards = catalog.commonCards();
        return RewardCatalog.chooseCommon(commonCards, random.nextInt(commonCards.size()));
    }

    /**
     * 读取启用中的卡组/卡片。卡组必须至少有一张稀有卡才会进入卡池，
     * 避免运营只录入部分数据时发布出不完整的卡组。
     */
    private RewardCatalog.Catalog loadCatalog() {
        List<CatalogRow> rows = jdbc.sql("""
                SELECT s.set_key, s.name AS set_name, s.theme, s.style, s.emblem,
                       s.color_a, s.color_b,
                       d.card_key, d.name AS card_name, d.image_path, d.rarity
                FROM public.card_sets AS s
                JOIN public.card_definitions AS d
                  ON d.set_key = s.set_key AND d.active = true
                WHERE s.active = true
                ORDER BY s.sort_order, s.set_key, d.sort_order, d.card_key
                """)
                .query((resultSet, rowNumber) -> new CatalogRow(
                        new RewardCatalog.CardSet(
                                resultSet.getString("set_key"),
                                resultSet.getString("set_name"),
                                resultSet.getString("theme"),
                                resultSet.getString("style"),
                                resultSet.getString("emblem"),
                                resultSet.getString("color_a"),
                                resultSet.getString("color_b")),
                        new RewardCatalog.CardDefinition(
                                resultSet.getString("card_key"),
                                resultSet.getString("set_key"),
                                resultSet.getString("card_name"),
                                resultSet.getString("image_path"),
                                resultSet.getString("rarity"))))
                .list();
        Map<String, SetAccumulator> bySetKey = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            SetAccumulator accumulator = bySetKey.computeIfAbsent(
                    row.definition().setKey(), key -> new SetAccumulator(row.set()));
            if (row.definition().rare()) {
                accumulator.rare = row.definition();
            } else {
                accumulator.commons.add(row.definition());
            }
        }
        List<RewardCatalog.CatalogSet> sets = bySetKey.values().stream()
                .filter(accumulator -> accumulator.rare != null)
                .map(accumulator -> new RewardCatalog.CatalogSet(
                        accumulator.set, accumulator.rare, accumulator.commons))
                .toList();
        return new RewardCatalog.Catalog(sets);
    }

    private CollectionStats collectionStats(long learnerId, long drawCount) {
        long totalCards = count("""
                SELECT COUNT(*) FROM public.card_collection WHERE student_id = :studentId
                """, learnerId);
        long uniqueCards = count("""
                SELECT COUNT(DISTINCT card_key) FROM public.card_collection WHERE student_id = :studentId
                """, learnerId);
        long totalCatalogCards = count("""
                SELECT COUNT(*)
                FROM public.card_definitions AS d
                JOIN public.card_sets AS s ON s.set_key = d.set_key AND s.active = true
                WHERE d.active = true
                """);
        long totalSets = count("""
                SELECT COUNT(*) FROM public.card_sets WHERE active = true
                """);
        return new CollectionStats(drawCount, totalCards, uniqueCards, totalCatalogCards, totalSets);
    }

    private PointsRow pointsRow(long learnerId) {
        return jdbc.sql("""
                SELECT points, lifetime_points, draw_count
                FROM public.user_points
                WHERE student_id = :studentId
                """).param("studentId", learnerId)
                .query(RewardService::mapPoints)
                .optional()
                .orElse(new PointsRow(0, 0, 0));
    }

    private long count(String sql, long learnerId) {
        return jdbc.sql(sql).param("studentId", learnerId).query(Long.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private UpCard toUpCard(RewardCatalog.CatalogSet upSet, ZonedDateTime now) {
        RewardCatalog.CardDefinition rare = upSet.rare();
        return new UpCard(upSet.set().key(), rare.cardKey(), rare.name(), upSet.set().name(),
                rare.imagePath(), RewardCatalog.nextRotation(now, zone));
    }

    private static CatalogSetView toCatalogSetView(RewardCatalog.CatalogSet set) {
        RewardCatalog.CardSet definition = set.set();
        return new CatalogSetView(definition.key(), definition.name(), definition.theme(),
                definition.style(), definition.emblem(), definition.colorA(), definition.colorB(),
                new CatalogCardView(set.rare().cardKey(), set.rare().name(), set.rare().imagePath(),
                        set.rare().rarity()),
                set.commons().stream()
                        .map(card -> new CatalogCardView(card.cardKey(), card.name(), card.imagePath(), card.rarity()))
                        .toList());
    }

    private static CardReward mapCard(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CardReward(
                resultSet.getLong("id"),
                resultSet.getString("card_key"),
                resultSet.getString("set_key"),
                resultSet.getString("rarity"),
                resultSet.getTimestamp("claimed_at").toInstant());
    }

    private static PointsRow mapPoints(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PointsRow(
                resultSet.getLong("points"),
                resultSet.getLong("lifetime_points"),
                resultSet.getLong("draw_count"));
    }

    record RewardSummary(long points, long lifetimePoints, List<CardReward> collection,
                         CollectionStats stats) {
    }

    record CardReward(long id, String cardKey, String setKey, String rarity, Instant claimedAt) {
    }

    record DrawnCard(long id, String cardKey, String setKey, String rarity, Instant claimedAt,
                     boolean isUp) {
    }

    record CollectionStats(long totalDraws, long totalCards, long uniqueCards,
                           long totalCatalogCards, long totalSets) {
    }

    record UpCard(String setKey, String cardKey, String name, String setName, String image,
                  OffsetDateTime rotatesAt) {
    }

    record CatalogCardView(String cardKey, String name, String image, String rarity) {
    }

    record CatalogSetView(String key, String name, String theme, String style, String emblem,
                          String colorA, String colorB, CatalogCardView rare,
                          List<CatalogCardView> commons) {
    }

    record CatalogResult(long drawCost, double rareRate, double upRateWhenRare, UpCard currentUp,
                         List<CatalogSetView> sets, int totalCards, int totalSets) {
    }

    record DrawResult(long points, long lifetimePoints, UpCard currentUp, List<DrawnCard> cards,
                      CollectionStats stats) {
    }

    private record PointsRow(long points, long lifetimePoints, long drawCount) {
    }

    private record CatalogRow(RewardCatalog.CardSet set, RewardCatalog.CardDefinition definition) {
    }

    private static final class SetAccumulator {
        private final RewardCatalog.CardSet set;
        private final List<RewardCatalog.CardDefinition> commons = new ArrayList<>();
        private RewardCatalog.CardDefinition rare;

        private SetAccumulator(RewardCatalog.CardSet set) {
            this.set = set;
        }
    }
}
