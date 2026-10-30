package com.tutorbase.administration.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员统计 HTTP 接口：首页概览、活动趋势与单个学员详情。
 *
 * <p>全部接口只对 {@code ADMINISTRATOR} 开放（由 Security 的 {@code /api/v1/admin/**} 规则保证），
 * 且学习数据只覆盖当前管理员的“我的课件”范围。
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminAnalyticsController {

    private static final int MIN_ACTIVITY_DAYS = 7;
    private static final int MAX_ACTIVITY_DAYS = 90;

    private final AdminAnalyticsService analytics;

    public AdminAnalyticsController(AdminAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/analytics/overview")
    ResponseEntity<OverviewResponse> overview(
            @AuthenticationPrincipal AccountPrincipal administrator) {
        return noStore(OverviewResponse.from(analytics.overview(administrator.accountId())));
    }

    @GetMapping("/analytics/activity")
    ResponseEntity<ActivityResponse> activity(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @RequestParam(required = false, defaultValue = "30") String days) {
        int parsedDays = parseActivityDays(days);
        return noStore(ActivityResponse.from(analytics.activity(administrator.accountId(), parsedDays)));
    }

    @GetMapping("/learners/{learnerId}/profile")
    ResponseEntity<LearnerProfileResponse> learnerProfile(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @PathVariable @Positive long learnerId) {
        return noStore(LearnerProfileResponse.from(
                analytics.learnerProfile(administrator.accountId(), learnerId)));
    }

    private static int parseActivityDays(String value) {
        try {
            int days = Integer.parseInt(value);
            if (days < MIN_ACTIVITY_DAYS || days > MAX_ACTIVITY_DAYS) {
                throw new InvalidAnalyticsRequest(
                        "days 参数必须在 " + MIN_ACTIVITY_DAYS + "～" + MAX_ACTIVITY_DAYS + " 之间。");
            }
            return days;
        } catch (NumberFormatException exception) {
            throw new InvalidAnalyticsRequest(
                    "days 参数必须为 " + MIN_ACTIVITY_DAYS + "～" + MAX_ACTIVITY_DAYS + " 之间的整数。",
                    exception);
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record OverviewResponse(
            long learners,
            long claimedLearners,
            long unclaimedLearners,
            long activeLearners7d,
            long pendingReviews,
            long submissions7d,
            CoursewareStatsResponse courseware,
            PendingReviewTargetResponse pendingReviewTarget) {

        static OverviewResponse from(AdminAnalyticsService.Overview overview) {
            return new OverviewResponse(
                    overview.learners(),
                    overview.claimedLearners(),
                    overview.unclaimedLearners(),
                    overview.activeLearners7d(),
                    overview.pendingReviews(),
                    overview.submissions7d(),
                    new CoursewareStatsResponse(
                            overview.courseware().visible(),
                            overview.courseware().unclaimed(),
                            overview.courseware().subjects()),
                    PendingReviewTargetResponse.from(overview.pendingReviewTarget()));
        }
    }

    record PendingReviewTargetResponse(long learnerId, long articleId, String questionId) {

        static PendingReviewTargetResponse from(AdminAnalyticsService.ReviewTarget target) {
            return target == null
                    ? null
                    : new PendingReviewTargetResponse(
                            target.learnerId(), target.articleId(), target.questionId());
        }
    }

    record CoursewareStatsResponse(long visible, long unclaimed, long subjects) {
    }

    record ActivityResponse(int days, List<ActivityPointResponse> points) {

        static ActivityResponse from(AdminAnalyticsService.Activity activity) {
            return new ActivityResponse(
                    activity.days(),
                    activity.points().stream().map(ActivityPointResponse::from).toList());
        }
    }

    record ActivityPointResponse(String date, long submissions, long reviews) {

        static ActivityPointResponse from(AdminAnalyticsService.ActivityPoint point) {
            return new ActivityPointResponse(
                    formatDate(point.date()), point.submissions(), point.reviews());
        }
    }

    record LearnerProfileResponse(
            long learnerId,
            String username,
            Long claimedByAccountId,
            String claimedByUsername,
            Instant lastActivityAt,
            long points,
            long lifetimePoints,
            long level,
            long checkInDays30,
            List<SubjectProgressResponse> subjectProgress,
            List<RecentSubmissionResponse> recentSubmissions,
            List<LearnerActivityPointResponse> activity) {

        static LearnerProfileResponse from(AdminAnalyticsService.LearnerProfile profile) {
            return new LearnerProfileResponse(
                    profile.learnerId(),
                    profile.username(),
                    profile.claimedByAccountId(),
                    profile.claimedByUsername(),
                    profile.lastActivityAt(),
                    profile.points(),
                    profile.lifetimePoints(),
                    profile.level(),
                    profile.checkInDays30(),
                    profile.subjectProgress().stream().map(SubjectProgressResponse::from).toList(),
                    profile.recentSubmissions().stream().map(RecentSubmissionResponse::from).toList(),
                    profile.activity().stream().map(LearnerActivityPointResponse::from).toList());
        }
    }

    record SubjectProgressResponse(String subject, long totalQuestions, long submittedQuestions,
                                   long pendingReviews) {

        static SubjectProgressResponse from(AdminAnalyticsService.SubjectProgress progress) {
            return new SubjectProgressResponse(
                    progress.subject(),
                    progress.totalQuestions(),
                    progress.submittedQuestions(),
                    progress.pendingReviews());
        }
    }

    record RecentSubmissionResponse(long articleId, String questionId, String reviewStatus,
                                    String reviewResult, Instant submittedAt) {

        static RecentSubmissionResponse from(AdminAnalyticsService.RecentSubmission submission) {
            return new RecentSubmissionResponse(
                    submission.articleId(),
                    submission.questionId(),
                    submission.reviewStatus(),
                    submission.reviewResult(),
                    submission.submittedAt());
        }
    }

    record LearnerActivityPointResponse(String date, long submissions) {

        static LearnerActivityPointResponse from(AdminAnalyticsService.LearnerActivityPoint point) {
            return new LearnerActivityPointResponse(formatDate(point.date()), point.submissions());
        }
    }

    private static String formatDate(LocalDate date) {
        return date.toString();
    }
}
