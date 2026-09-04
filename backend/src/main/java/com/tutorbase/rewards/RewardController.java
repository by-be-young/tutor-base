package com.tutorbase.rewards;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rewards")
@Validated
public class RewardController {

    private final RewardService rewards;

    RewardController(RewardService rewards) {
        this.rewards = rewards;
    }

    @GetMapping
    ResponseEntity<RewardSummaryResponse> summary(@AuthenticationPrincipal AccountPrincipal principal) {
        long learnerId = requireLearner(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RewardSummaryResponse.from(rewards.summary(learnerId)));
    }

    @PostMapping("/milestones/{milestonePoints}/claims")
    ResponseEntity<CardRewardResponse> claim(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Min(RewardCatalog.MILESTONE_STEP) @Max(RewardCatalog.MAX_MILESTONE)
            long milestonePoints) {
        long learnerId = requireLearner(principal);
        CardRewardResponse response = CardRewardResponse.from(rewards.claim(learnerId, milestonePoints));
        return ResponseEntity.created(URI.create("/api/v1/rewards/milestones/" + milestonePoints + "/claims"))
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        return principal.learnerId();
    }

    record RewardSummaryResponse(long points, List<CardRewardResponse> collection) {
        static RewardSummaryResponse from(RewardService.RewardSummary summary) {
            return new RewardSummaryResponse(
                    summary.points(),
                    summary.collection().stream().map(CardRewardResponse::from).toList());
        }
    }

    record CardRewardResponse(long id, long milestonePoints, String cardKey, String setKey, String rarity,
                              Instant claimedAt) {
        static CardRewardResponse from(RewardService.CardReward reward) {
            return new CardRewardResponse(reward.id(), reward.milestonePoints(), reward.cardKey(),
                    reward.setKey(), reward.rarity(), reward.claimedAt());
        }
    }
}
