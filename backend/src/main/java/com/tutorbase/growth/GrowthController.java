package com.tutorbase.growth;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 成长中心 HTTP 接口：成就 + 每日/每周/限时任务。 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class GrowthController {

    private final GrowthService growth;

    GrowthController(GrowthService growth) {
        this.growth = growth;
    }

    @GetMapping("/achievements")
    ResponseEntity<GrowthService.AchievementSummary> achievements(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(growth.achievements(requireLearner(principal)));
    }

    @PostMapping("/achievements/{key}/claims")
    ResponseEntity<GrowthService.ClaimResult> claimAchievement(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Pattern(regexp = "[a-z0-9-]{1,64}") String key) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(growth.claimAchievement(requireLearner(principal), key));
    }

    @GetMapping("/tasks/board")
    ResponseEntity<GrowthService.TaskBoard> taskBoard(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(growth.taskBoard(requireLearner(principal)));
    }

    @PostMapping("/tasks/{key}/accept")
    ResponseEntity<Void> acceptTask(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Pattern(regexp = "[a-z0-9-]{1,64}") String key) {
        growth.acceptTask(requireLearner(principal), key);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/tasks/{key}/claims")
    ResponseEntity<GrowthService.ClaimResult> claimTask(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Pattern(regexp = "[a-z0-9-]{1,64}") String key) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(growth.claimTask(requireLearner(principal), key));
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        return principal.learnerId();
    }
}
