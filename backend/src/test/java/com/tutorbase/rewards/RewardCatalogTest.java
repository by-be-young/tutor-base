package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class RewardCatalogTest {

    @Test
    void containsSixCommonCardsForEveryPublishedSet() {
        assertThat(RewardCatalog.commonCards()).hasSize(60);
        assertThat(RewardCatalog.commonCards().stream()
                .map(RewardCatalog.CardDefinition::setKey)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "puppy", "ocean", "cosmos", "dessert", "sport",
                        "milkfrog", "delta", "opossum", "verify", "softgun");
        assertThat(RewardCatalog.commonCards())
                .allSatisfy(card -> assertThat(card.rarity()).isEqualTo("common"));
    }

    @Test
    void containsOneRareCardForEveryPublishedSet() {
        assertThat(RewardCatalog.rareCards()).hasSize(10);
        assertThat(RewardCatalog.rareCards())
                .allSatisfy(card -> {
                    assertThat(card.rarity()).isEqualTo("rare");
                    assertThat(card.cardKey()).isEqualTo(card.setKey() + "-r");
                });
        assertThat(RewardCatalog.rareCards().stream()
                .map(RewardCatalog.CardDefinition::setKey)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "puppy", "ocean", "cosmos", "dessert", "sport",
                        "milkfrog", "delta", "opossum", "verify", "softgun");
    }
}
