package com.tutorbase.rewards;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/** Server-owned collectible-card catalog and draw policy. */
final class RewardCatalog {

    private static final List<String> SET_KEYS = List.of(
            "puppy", "ocean", "cosmos", "dessert", "sport",
            "milkfrog", "delta", "opossum", "verify", "softgun");
    private static final List<CardDefinition> COMMON_CARDS = SET_KEYS.stream()
            .flatMap(setKey -> IntStream.range(0, 6)
                    .mapToObj(slot -> new CardDefinition(setKey + "-c" + slot, setKey, "common")))
            .toList();
    private static final List<CardDefinition> RARE_CARDS = SET_KEYS.stream()
            .map(setKey -> new CardDefinition(setKey + "-r", setKey, "rare"))
            .toList();

    private RewardCatalog() {
    }

    record CardDefinition(String cardKey, String setKey, String rarity) {
    }

    static List<CardDefinition> commonCards() {
        return COMMON_CARDS;
    }

    static List<CardDefinition> rareCards() {
        return RARE_CARDS;
    }
}
