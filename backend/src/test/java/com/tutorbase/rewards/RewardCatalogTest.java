package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RewardCatalogTest {

    @Test
    void drawsTheOnlyMissingCommonCardBeforeAllowingDuplicates() {
        Set<String> owned = new HashSet<>();
        for (String setKey : Set.of("puppy", "ocean", "cosmos", "dessert", "sport")) {
            for (int slot = 0; slot < 6; slot++) {
                owned.add(setKey + "-c" + slot);
            }
        }
        owned.remove("sport-c5");

        assertThat(RewardCatalog.cardFor(200, owned))
                .isEqualTo(new RewardCatalog.CardDefinition("sport-c5", "sport", "common"));
    }

    @Test
    void rareMilestonesDrawOnlyRareCards() {
        assertThat(RewardCatalog.cardFor(1_000, Set.of()))
                .satisfies(card -> {
                    assertThat(card.rarity()).isEqualTo("rare");
                    assertThat(card.cardKey()).isEqualTo(card.setKey() + "-r");
                    assertThat(Set.of("puppy", "ocean", "cosmos", "dessert", "sport"))
                            .contains(card.setKey());
                });
    }

    @Test
    void rejectsValuesOutsideThePublishedMilestoneCatalog() {
        assertThatThrownBy(() -> RewardCatalog.cardFor(0, Set.of())).isInstanceOf(InvalidRewardMilestone.class);
        assertThatThrownBy(() -> RewardCatalog.cardFor(201, Set.of())).isInstanceOf(InvalidRewardMilestone.class);
        assertThatThrownBy(() -> RewardCatalog.cardFor(6_200, Set.of())).isInstanceOf(InvalidRewardMilestone.class);
    }
}
