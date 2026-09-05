package com.tutorbase.rewards;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/** Server-owned collectible-card catalog and draw policy. */
final class RewardCatalog {

    static final int MILESTONE_STEP = 200;
    static final int MAX_MILESTONE = 6_000;

    private static final List<String> SET_KEYS = List.of("puppy", "ocean", "cosmos", "dessert", "sport");
    private static final List<CardDefinition> COMMON_CARDS = SET_KEYS.stream()
            .flatMap(setKey -> IntStream.range(0, 6)
                    .mapToObj(slot -> new CardDefinition(setKey + "-c" + slot, setKey, "common")))
            .toList();
    private static final List<CardDefinition> RARE_CARDS = SET_KEYS.stream()
            .map(setKey -> new CardDefinition(setKey + "-r", setKey, "rare"))
            .toList();

    private RewardCatalog() {
    }

    static CardDefinition cardFor(long milestonePoints, Set<String> ownedCardKeys) {
        if (milestonePoints < MILESTONE_STEP
                || milestonePoints > MAX_MILESTONE
                || milestonePoints % MILESTONE_STEP != 0) {
            throw new InvalidRewardMilestone();
        }

        List<CardDefinition> pool = milestonePoints % 1_000 == 0 ? RARE_CARDS : COMMON_CARDS;
        List<CardDefinition> missing = pool.stream()
                .filter(card -> !ownedCardKeys.contains(card.cardKey()))
                .toList();
        List<CardDefinition> candidates = missing.isEmpty() ? pool : missing;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    record CardDefinition(String cardKey, String setKey, String rarity) {
    }
}
