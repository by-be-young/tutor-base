package com.tutorbase.rewards;

import java.util.List;

/** Server-owned deterministic mapping from a points milestone to its collectible card. */
final class RewardCatalog {

    static final int MILESTONE_STEP = 200;
    static final int MAX_MILESTONE = 6_000;

    private static final List<String> SET_KEYS = List.of("flame", "aurora", "galaxy", "forest");

    private RewardCatalog() {
    }

    static CardDefinition cardFor(long milestonePoints) {
        if (milestonePoints < MILESTONE_STEP
                || milestonePoints > MAX_MILESTONE
                || milestonePoints % MILESTONE_STEP != 0) {
            throw new InvalidRewardMilestone();
        }

        if (milestonePoints % 1_000 == 0) {
            int setIndex = Math.floorMod((int) (milestonePoints / 1_000) - 1, SET_KEYS.size());
            String setKey = SET_KEYS.get(setIndex);
            return new CardDefinition(setKey + "-r", setKey, "rare");
        }

        int ordinal = (int) (milestonePoints / MILESTONE_STEP) - 1;
        String setKey = SET_KEYS.get(Math.floorMod(ordinal / 6, SET_KEYS.size()));
        int slot = Math.floorMod(ordinal, 6);
        return new CardDefinition(setKey + "-c" + slot, setKey, "common");
    }

    record CardDefinition(String cardKey, String setKey, String rarity) {
    }
}
