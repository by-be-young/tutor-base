package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RewardCatalogTest {

    @Test
    void mapsCommonAndRareMilestonesDeterministically() {
        assertThat(RewardCatalog.cardFor(200))
                .isEqualTo(new RewardCatalog.CardDefinition("flame-c0", "flame", "common"));
        assertThat(RewardCatalog.cardFor(1_000))
                .isEqualTo(new RewardCatalog.CardDefinition("flame-r", "flame", "rare"));
        assertThat(RewardCatalog.cardFor(1_200))
                .isEqualTo(new RewardCatalog.CardDefinition("flame-c5", "flame", "common"));
        assertThat(RewardCatalog.cardFor(1_400))
                .isEqualTo(new RewardCatalog.CardDefinition("aurora-c0", "aurora", "common"));
        assertThat(RewardCatalog.cardFor(4_000))
                .isEqualTo(new RewardCatalog.CardDefinition("forest-r", "forest", "rare"));
        assertThat(RewardCatalog.cardFor(5_000))
                .isEqualTo(new RewardCatalog.CardDefinition("flame-r", "flame", "rare"));
    }

    @Test
    void rejectsValuesOutsideThePublishedMilestoneCatalog() {
        assertThatThrownBy(() -> RewardCatalog.cardFor(0)).isInstanceOf(InvalidRewardMilestone.class);
        assertThatThrownBy(() -> RewardCatalog.cardFor(201)).isInstanceOf(InvalidRewardMilestone.class);
        assertThatThrownBy(() -> RewardCatalog.cardFor(6_200)).isInstanceOf(InvalidRewardMilestone.class);
    }
}
