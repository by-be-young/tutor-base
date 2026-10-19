package com.tutorbase.growth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AchievementCatalogTest {

    @Test
    void everyAchievementHasStrictlyIncreasingRequirementsAndRewards() {
        assertThat(AchievementCatalog.ALL).isNotEmpty();
        for (AchievementCatalog.Achievement achievement : AchievementCatalog.ALL) {
            var tiers = achievement.tiers();
            assertThat(tiers).isNotEmpty();
            for (int index = 1; index < tiers.size(); index++) {
                assertThat(tiers.get(index).level()).isEqualTo(index + 1);
                assertThat(tiers.get(index).requirement()).isGreaterThan(tiers.get(index - 1).requirement());
                assertThat(tiers.get(index).rewardPoints()).isGreaterThan(tiers.get(index - 1).rewardPoints());
            }
        }
    }

    @Test
    void keysAreUniqueAndLookupRejectsUnknownKeys() {
        assertThat(AchievementCatalog.ALL.stream().map(AchievementCatalog.Achievement::key))
                .doesNotHaveDuplicates();
        assertThat(AchievementCatalog.find("mastery")).isPresent();
        assertThat(AchievementCatalog.find("not-an-achievement")).isEmpty();
    }
}
