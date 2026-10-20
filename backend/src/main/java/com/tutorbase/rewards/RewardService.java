package com.tutorbase.rewards;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the rewards interface: callers may read their balance/collection and draw cards.
 * Eligibility, server-owned card selection and persistence are deliberately kept in one transaction.
 */
@Service
class RewardService {

    private static final long DRAW_COST = 80;

    private final JdbcClient jdbc;

    RewardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    RewardSummary summary(long learnerId) {
        long points = jdbc.sql("SELECT points FROM public.user_points WHERE student_id = :studentId")
                .param("studentId", learnerId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        List<CardReward> cards = jdbc.sql("""
                SELECT id, card_key, set_key, rarity, claimed_at
                FROM public.card_collection
                WHERE student_id = :studentId
                ORDER BY claimed_at DESC, id DESC
                """)
                .param("studentId", learnerId)
                .query(CardReward::map)
                .list();
        long lifetimePoints = jdbc.sql("SELECT lifetime_points FROM public.user_points WHERE student_id = :studentId")
                .param("studentId", learnerId).query(Long.class).optional().orElse(points);
        return new RewardSummary(points, lifetimePoints, cards);
    }

    @Transactional
    DrawResult draw(long learnerId, int count) {
        jdbc.sql("""
                INSERT INTO public.user_points (student_id, points, updated_at)
                VALUES (:studentId, 0, now())
                ON CONFLICT (student_id) DO NOTHING
                """).param("studentId", learnerId).update();
        long points = jdbc.sql("SELECT points FROM public.user_points WHERE student_id = :studentId FOR UPDATE")
                .param("studentId", learnerId).query(Long.class).single();
        long cost = DRAW_COST * count;
        if (points < cost) throw new RewardInsufficientPoints();
        List<CardReward> cards = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<RewardCatalog.CardDefinition> pool = Math.random() < 0.05
                    ? RewardCatalog.rareCards() : RewardCatalog.commonCards();
            RewardCatalog.CardDefinition definition = pool.get(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
            CardReward card = jdbc.sql("""
                    INSERT INTO public.card_collection
                        (student_id, card_key, set_key, rarity)
                    VALUES (:studentId, :cardKey, :setKey, :rarity)
                        RETURNING id, card_key, set_key, rarity, claimed_at
                    """).param("studentId", learnerId).param("cardKey", definition.cardKey())
                    .param("setKey", definition.setKey()).param("rarity", definition.rarity())
                    .query(CardReward::map).single();
            cards.add(card);
        }
        long remaining = points - cost;
        jdbc.sql("UPDATE public.user_points SET points = :points, updated_at = now() WHERE student_id = :studentId")
                .param("points", remaining).param("studentId", learnerId).update();
        long lifetimePoints = jdbc.sql("SELECT lifetime_points FROM public.user_points WHERE student_id = :studentId")
                .param("studentId", learnerId).query(Long.class).single();
        return new DrawResult(remaining, lifetimePoints, cards);
    }

    record RewardSummary(long points, long lifetimePoints, List<CardReward> collection) {
    }

    record CardReward(long id, String cardKey, String setKey, String rarity,
                      Instant claimedAt) {
        static CardReward map(ResultSet resultSet, int rowNumber) throws SQLException {
            return new CardReward(
                    resultSet.getLong("id"),
                    resultSet.getString("card_key"),
                    resultSet.getString("set_key"),
                    resultSet.getString("rarity"),
                    resultSet.getTimestamp("claimed_at").toInstant());
        }

    }

    record DrawResult(long points, long lifetimePoints, List<CardReward> cards) {
    }
}
