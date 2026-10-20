package com.tutorbase.rewards;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端卡池规则：周 UP 轮换、稀有/普通抽取。目录数据来自数据库（card_sets /
 * card_definitions），本类不保存任何写死的卡组、卡片或数量。
 *
 * <p>所有挑选方法都是纯函数，方便单元测试注入固定的周数/roll/索引。</p>
 */
final class RewardCatalog {

    static final long DRAW_COST = 80;
    static final double RARE_RATE = 0.05;
    static final double UP_RATE_WHEN_RARE = 0.5;

    private RewardCatalog() {
    }

    record CardSet(String key, String name, String theme, String style, String emblem,
                   String colorA, String colorB) {
    }

    record CardDefinition(String cardKey, String setKey, String name, String imagePath, String rarity) {
        boolean rare() {
            return "rare".equals(rarity);
        }
    }

    /** 一个启用中的卡组，包含它的稀有卡和全部普通卡。 */
    record CatalogSet(CardSet set, CardDefinition rare, List<CardDefinition> commons) {
        CatalogSet {
            commons = List.copyOf(commons);
        }

        List<CardDefinition> cards() {
            List<CardDefinition> cards = new ArrayList<>(commons.size() + 1);
            if (rare != null) {
                cards.add(rare);
            }
            cards.addAll(commons);
            return cards;
        }
    }

    /** 启用中的卡池，顺序即卡组 sort_order,set_key 顺序。 */
    record Catalog(List<CatalogSet> sets) {
        Catalog {
            sets = List.copyOf(sets);
        }

        List<CardDefinition> rareCards() {
            return sets.stream().map(CatalogSet::rare).filter(java.util.Objects::nonNull).toList();
        }

        List<CardDefinition> commonCards() {
            return sets.stream().flatMap(set -> set.commons().stream()).toList();
        }

        CatalogSet upSet(int isoWeek) {
            if (sets.isEmpty()) {
                throw new IllegalStateException("card catalog is empty");
            }
            CatalogSet candidate = sets.get(upSetIndex(isoWeek, sets.size()));
            if (candidate.rare() != null) {
                return candidate;
            }
            return sets.stream()
                    .filter(set -> set.rare() != null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("card catalog has no rare card"));
        }

        CardDefinition upCard(int isoWeek) {
            return upSet(isoWeek).rare();
        }

        int totalCards() {
            return sets.stream().mapToInt(set -> set.cards().size()).sum();
        }

        int totalSets() {
            return sets.size();
        }
    }

    /** ISO 周数（1–53），用于确定当期 UP。 */
    static int isoWeek(ZonedDateTime now) {
        return now.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * UP 卡组下标：按 ISO 周数对当前启用卡组数量取模，确定性轮换。
     * 不依赖任何写死的 10/5 等数量。
     */
    static int upSetIndex(int isoWeek, int activeSetCount) {
        if (activeSetCount <= 0) {
            throw new IllegalArgumentException("activeSetCount must be positive");
        }
        return Math.floorMod(isoWeek - 1, activeSetCount);
    }

    /** 下一期 UP 生效时间：下一个周一 00:00（目标时区）。 */
    static OffsetDateTime nextRotation(ZonedDateTime now, ZoneId zone) {
        LocalDate today = now.toLocalDate();
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.atStartOfDay(zone).toOffsetDateTime();
    }

    /**
     * 稀有卡选择：命中 UP 抽取时返回当期 UP；否则在其余启用稀有卡中均匀抽取。
     * 只有 UP 一张稀有卡时退化为必然返回它。
     */
    static CardDefinition chooseRare(List<CardDefinition> rareCards, CardDefinition upCard,
                                     boolean upRoll, int otherIndex) {
        if (rareCards.isEmpty()) {
            throw new IllegalArgumentException("rare card pool is empty");
        }
        if (upCard != null && upRoll) {
            return upCard;
        }
        List<CardDefinition> others = rareCards.stream()
                .filter(card -> upCard == null || !card.cardKey().equals(upCard.cardKey()))
                .toList();
        if (others.isEmpty()) {
            return upCard != null ? upCard : rareCards.get(Math.floorMod(otherIndex, rareCards.size()));
        }
        return others.get(Math.floorMod(otherIndex, others.size()));
    }

    /** 普通卡选择：全部启用普通卡中均匀抽取。 */
    static CardDefinition chooseCommon(List<CardDefinition> commonCards, int index) {
        if (commonCards.isEmpty()) {
            throw new IllegalArgumentException("common card pool is empty");
        }
        return commonCards.get(Math.floorMod(index, commonCards.size()));
    }
}
