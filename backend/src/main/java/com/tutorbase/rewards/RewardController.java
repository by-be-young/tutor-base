package com.tutorbase.rewards;

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
    ResponseEntity<RewardService.RewardSummary> summary(@AuthenticationPrincipal AccountPrincipal principal) {
        long learnerId = requireLearner(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(rewards.summary(learnerId));
    }

    @GetMapping("/catalog")
    ResponseEntity<RewardService.CatalogResult> catalog(@AuthenticationPrincipal AccountPrincipal principal) {
        requireLearner(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(rewards.catalog());
    }

    @PostMapping("/draws")
    ResponseEntity<RewardService.DrawResult> draw(@AuthenticationPrincipal AccountPrincipal principal,
                                                  @RequestBody @jakarta.validation.Valid DrawRequest request) {
        long learnerId = requireLearner(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(rewards.draw(learnerId, request.count()));
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        return principal.learnerId();
    }

    record DrawRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int count) {
    }
}
