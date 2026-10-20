package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tutorbase.rewards.RewardCatalog.CardDefinition;
import com.tutorbase.rewards.RewardCatalog.CardSet;
import com.tutorbase.rewards.RewardCatalog.Catalog;
import com.tutorbase.rewards.RewardCatalog.CatalogSet;

/**
 * 卡池规则纯单元测试：不依赖数据库，也不假设固定 10 个卡组或 6 张普通卡。
 */
class RewardCatalogTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void upSetIndexRotatesByIsoWeekAndScalesWithActiveSetCount() {
        assertThat(RewardCatalog.upSetIndex(1, 10)).isZero();
        assertThat(RewardCatalog.upSetIndex(10, 10)).isEqualTo(9);
        assertThat(RewardCatalog.upSetIndex(11, 10)).isZero();
        assertThat(RewardCatalog.upSetIndex(1, 3)).isZero();
        assertThat(RewardCatalog.upSetIndex(2, 3)).isEqualTo(1);
        assertThat(RewardCatalog.upSetIndex(3, 3)).isEqualTo(2);
        assertThat(RewardCatalog.upSetIndex(4, 3)).isZero();
        assertThat(RewardCatalog.upSetIndex(53, 1)).isZero();

        for (int activeSets = 1; activeSets <= 25; activeSets++) {
            for (int week = 1; week <= 53; week++) {
                assertThat(RewardCatalog.upSetIndex(week, activeSets))
                        .isBetween(0, activeSets - 1);
            }
        }
    }

    @Test
    void upSetIndexRejectsEmptyCatalog() {
        assertThatThrownBy(() -> RewardCatalog.upSetIndex(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isoWeekFollowsIsoWeekBasedYear() {
        assertThat(RewardCatalog.isoWeek(ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, SHANGHAI)))
                .isEqualTo(1);
        assertThat(RewardCatalog.isoWeek(ZonedDateTime.of(2025, 12, 29, 0, 0, 0, 0, SHANGHAI)))
                .isEqualTo(1);
        assertThat(RewardCatalog.isoWeek(ZonedDateTime.of(2026, 10, 20, 21, 0, 0, 0, SHANGHAI)))
                .isEqualTo(43);
    }

    @Test
    void weeklyUpUsesOrderedSetListAndWraps() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta"), set("gamma")));

        assertThat(catalog.upSet(1).set().key()).isEqualTo("alpha");
        assertThat(catalog.upSet(2).set().key()).isEqualTo("beta");
        assertThat(catalog.upSet(3).set().key()).isEqualTo("gamma");
        assertThat(catalog.upSet(4).set().key()).isEqualTo("alpha");
        assertThat(catalog.upSet(53).set().key()).isEqualTo("beta");
    }

    @Test
    void nextRotationIsNextMondayMidnightInShanghai() {
        assertThat(RewardCatalog.nextRotation(
                ZonedDateTime.of(2026, 10, 20, 21, 51, 0, 0, SHANGHAI), SHANGHAI))
                .isEqualTo(ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, SHANGHAI).toOffsetDateTime());
        // 周一当天也返回下一个周一，而不是当天 00:00。
        assertThat(RewardCatalog.nextRotation(
                ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, SHANGHAI), SHANGHAI))
                .isEqualTo(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, SHANGHAI).toOffsetDateTime());
        assertThat(RewardCatalog.nextRotation(
                ZonedDateTime.of(2026, 10, 20, 21, 51, 0, 0, SHANGHAI), SHANGHAI).getOffset())
                .isEqualTo(java.time.ZoneOffset.ofHours(8));
    }

    @Test
    void rareDrawReturnsUpCardWhenUpRollSucceeds() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta")));
        CardDefinition up = catalog.rareCards().get(0);

        assertThat(RewardCatalog.chooseRare(catalog.rareCards(), up, true, 0)).isEqualTo(up);
    }

    @Test
    void rareDrawOtherwisePicksOnlyNonUpCards() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta"), set("gamma")));
        CardDefinition up = catalog.rareCards().get(1);

        for (int index = 0; index < 100; index++) {
            CardDefinition picked = RewardCatalog.chooseRare(catalog.rareCards(), up, false, index);
            assertThat(picked.cardKey()).isNotEqualTo(up.cardKey());
            assertThat(picked.rarity()).isEqualTo("rare");
        }
        // 其余稀有卡都能被均匀抽到。
        assertThat(java.util.stream.IntStream.range(0, 2)
                .mapToObj(index -> RewardCatalog.chooseRare(catalog.rareCards(), up, false, index).cardKey())
                .distinct())
                .hasSize(2);
    }

    @Test
    void rareDrawFallsBackToUpCardWhenItIsTheOnlyRareCard() {
        Catalog catalog = new Catalog(List.of(set("alpha")));
        CardDefinition up = catalog.rareCards().get(0);

        assertThat(RewardCatalog.chooseRare(catalog.rareCards(), up, false, 7)).isEqualTo(up);
        assertThat(RewardCatalog.chooseRare(catalog.rareCards(), up, true, 7)).isEqualTo(up);
    }

    @Test
    void rareDrawWithoutUpCardUsesFullRarePool() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta")));

        assertThat(RewardCatalog.chooseRare(catalog.rareCards(), null, false, 0).cardKey())
                .isEqualTo("alpha-r");
        assertThat(RewardCatalog.chooseRare(catalog.rareCards(), null, false, 1).cardKey())
                .isEqualTo("beta-r");
    }

    @Test
    void commonDrawUsesUniformIndexOverAllActiveCommons() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta")));

        assertThat(catalog.commonCards()).hasSize(4);
        assertThat(RewardCatalog.chooseCommon(catalog.commonCards(), 0).cardKey())
                .isEqualTo(catalog.commonCards().get(0).cardKey());
        assertThat(RewardCatalog.chooseCommon(catalog.commonCards(), 4).cardKey())
                .isEqualTo(catalog.commonCards().get(0).cardKey());
        assertThat(java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> RewardCatalog.chooseCommon(catalog.commonCards(), index).cardKey())
                .distinct())
                .hasSize(4);
    }

    @Test
    void countsAreDerivedFromCatalogDataNotHardcoded() {
        Catalog catalog = new Catalog(List.of(set("alpha"), set("beta"), set("gamma")));

        assertThat(catalog.totalSets()).isEqualTo(3);
        assertThat(catalog.totalCards()).isEqualTo(9);
        assertThat(catalog.rareCards()).hasSize(3);
        assertThat(catalog.commonCards()).hasSize(6);
    }

    private static CatalogSet set(String setKey) {
        CardSet set = new CardSet(setKey, setKey + "-name", "theme", "style", "fas fa-star",
                "#111111", "#222222");
        List<CardDefinition> commons = java.util.stream.IntStream.range(0, 2)
                .mapToObj(index -> new CardDefinition(setKey + "-c" + index, setKey,
                        setKey + "-common-" + index, "cards/" + setKey + "-c" + index + ".png", "common"))
                .toList();
        CardDefinition rare = new CardDefinition(setKey + "-r", setKey, setKey + "-rare",
                "cards/" + setKey + "-r.png", "rare");
        return new CatalogSet(set, rare, commons);
    }
}