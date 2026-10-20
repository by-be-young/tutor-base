package com.tutorbase.rewards;

import java.time.Instant;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/draws")
    ResponseEntity<DrawResponse> draw(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestBody @jakarta.validation.Valid DrawRequest request) {
        long learnerId = requireLearner(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DrawResponse.from(rewards.draw(learnerId, request.count())));
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        return principal.learnerId();
    }

    record RewardSummaryResponse(long points, long lifetimePoints, List<CardRewardResponse> collection) {
        static RewardSummaryResponse from(RewardService.RewardSummary summary) {
            return new RewardSummaryResponse(
                    summary.points(), summary.lifetimePoints(),
                    summary.collection().stream().map(CardRewardResponse::from).toList());
        }
    }

    record CardRewardResponse(long id, String cardKey, String setKey, String rarity,
                              Instant claimedAt) {
        static CardRewardResponse from(RewardService.CardReward reward) {
            return new CardRewardResponse(reward.id(), reward.cardKey(),
                    reward.setKey(), reward.rarity(), reward.claimedAt());
        }

    }

    record DrawRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int count) {
    }

    record DrawResponse(long points, long lifetimePoints, List<CardRewardResponse> cards) {
        static DrawResponse from(RewardService.DrawResult result) {
            return new DrawResponse(result.points(), result.lifetimePoints(),
                    result.cards().stream().map(CardRewardResponse::from).toList());
        }
    }
}
