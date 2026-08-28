package com.tutorbase.checkin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkins")
@Validated
public class CheckInController {

    private final CheckInService checkIns;

    CheckInController(CheckInService checkIns) {
        this.checkIns = checkIns;
    }

    /** GET /api/v1/checkins?year=2026&month=8 — 指定自然月的签到概览。 */
    @GetMapping
    ResponseEntity<MonthOverviewResponse> overview(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        requireLearner(principal);
        CheckInService.MonthOverview overview = checkIns.overview(principal.learnerId(), YearMonth.of(year, month));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MonthOverviewResponse.from(year, month, overview));
    }

    /** POST /api/v1/checkins — 执行当日签到；同日重复调用幂等返回已签到状态。 */
    @PostMapping
    ResponseEntity<CheckInResponse> checkIn(@AuthenticationPrincipal AccountPrincipal principal) {
        requireLearner(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CheckInResponse.from(checkIns.checkIn(principal.learnerId())));
    }

    private static void requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
    }

    record MonthOverviewResponse(int year, int month, List<String> dates, int monthCount,
                                 boolean todayCheckedIn, String today,
                                 int pointsPerCheckIn, List<Integer> bonusMilestones) {
        static MonthOverviewResponse from(int year, int month, CheckInService.MonthOverview overview) {
            return new MonthOverviewResponse(
                    year,
                    month,
                    overview.dates().stream().map(LocalDate::toString).toList(),
                    overview.dates().size(),
                    overview.todayCheckedIn(),
                    overview.today().toString(),
                    CheckInService.BASE_POINTS,
                    CheckInService.BONUS_MILESTONES);
        }
    }

    record CheckInResponse(String date, long pointsAwarded, Integer bonusDays,
                           int monthCount, boolean alreadyCheckedIn, long totalPoints) {
        static CheckInResponse from(CheckInService.CheckInResult result) {
            return new CheckInResponse(result.date().toString(), result.pointsAwarded(),
                    result.bonusDays(), result.monthCount(), result.alreadyCheckedIn(), result.totalPoints());
        }
    }
}
