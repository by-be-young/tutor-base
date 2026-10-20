package com.tutorbase.growth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AchievementCatalogTest {

    private static final int CURRENT_CATALOG_CARDS = 70;

    @Test
    void everyAchievementHasStrictlyIncreasingRequirementsAndRewards() {
        for (int totalCards : new int[]{CURRENT_CATALOG_CARDS, 140}) {
            List<AchievementCatalog.Achievement> catalog = AchievementCatalog.all(totalCards);
            assertThat(catalog).isNotEmpty();
            for (AchievementCatalog.Achievement achievement : catalog) {
                var tiers = achievement.tiers();
                assertThat(tiers).isNotEmpty();
                for (int index = 1; index < tiers.size(); index++) {
                    assertThat(tiers.get(index).level()).isEqualTo(index + 1);
                    assertThat(tiers.get(index).requirement()).isGreaterThan(tiers.get(index - 1).requirement());
                    assertThat(tiers.get(index).rewardPoints()).isGreaterThan(tiers.get(index - 1).rewardPoints());
                }
            }
        }
    }

    @Test
    void uniqueCardTiersScaleWithActiveCatalogSize() {
        var at70 = AchievementCatalog.cardsAchievement(70);
        var at140 = AchievementCatalog.cardsAchievement(140);

        assertThat(at70.metric()).isEqualTo(AchievementCatalog.Metric.UNIQUE_CARDS);
        assertThat(at140.metric()).isEqualTo(AchievementCatalog.Metric.UNIQUE_CARDS);
        assertThat(at140.tiers().get(at140.tiers().size() - 1).requirement())
                .isGreaterThan(at70.tiers().get(at70.tiers().size() - 1).requirement());
        // 最后一档要求不超过卡池总量，避免出现无法达成的等级。
        assertThat(at70.tiers()).allSatisfy(tier ->
                assertThat(tier.requirement()).isLessThanOrEqualTo(70));
        assertThat(at140.tiers()).allSatisfy(tier ->
                assertThat(tier.requirement()).isLessThanOrEqualTo(140));
    }

    @Test
    void obtainedCardsAchievementCountsDuplicatesSeparately() {
        var obtained = AchievementCatalog.find(CURRENT_CATALOG_CARDS, "obtained");
        assertThat(obtained).isPresent();
        assertThat(obtained.get().metric()).isEqualTo(AchievementCatalog.Metric.OBTAINED_CARDS);
        assertThat(obtained.get().tiers()).isNotEmpty();

        var unique = AchievementCatalog.find(CURRENT_CATALOG_CARDS, "cards");
        assertThat(unique).isPresent();
        assertThat(unique.get().metric()).isEqualTo(AchievementCatalog.Metric.UNIQUE_CARDS);
    }

    @Test
    void keysAreUniqueAndLookupRejectsUnknownKeys() {
        assertThat(AchievementCatalog.all(CURRENT_CATALOG_CARDS).stream()
                .map(AchievementCatalog.Achievement::key))
                .doesNotHaveDuplicates();
        assertThat(AchievementCatalog.find(CURRENT_CATALOG_CARDS, "mastery")).isPresent();
        assertThat(AchievementCatalog.find(CURRENT_CATALOG_CARDS, "not-an-achievement")).isEmpty();
    }
}
