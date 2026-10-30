package com.tutorbase.administration.analytics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tutorbase.administration.courseware.CoursewareScope;
import com.tutorbase.administration.learner.LearnerQuery;
import com.tutorbase.shared.security.LearnerClaimAccess;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员只读统计：概览、活动趋势与单个学员详情。
 *
 * <p>学习数据（作答、待批阅、分学科进度、近期作答与每日提交）只统计当前管理员的
 * “我的课件”范围；学习者目录、积分与签到是学员全局数据。可见范围复用
 * {@link CoursewareScope}，不在此处复制认领判定。
 */
@Service
class AdminAnalyticsService {

    private static final int PROFILE_ACTIVITY_DAYS = 30;
    private static final int RECENT_SUBMISSION_LIMIT = 10;
    private static final int POINTS_PER_LEVEL = 200;

    /**
     * 与 LearnerSql 的 last_activity_at 聚合口径一致：作答、错题、签到与登录会话的最新时间。
     * 学习者目录排除关联 administrator 账户的 learner 行；统计接口再排除测试学员。
     */
    private static final String LEARNER_ACTIVITY_SQL = """
            WITH learner_activity AS (
                SELECT s.id,
                       s.claimed_by,
                       GREATEST(
                           (SELECT max(submission.updated_at)
                            FROM public.article_question_submissions AS submission
                            WHERE submission.student_id = s.id),
                           (SELECT max(wrong.updated_at)
                            FROM public.wrong_questions AS wrong
                            WHERE wrong.student_id = s.id),
                           (SELECT max(check_in.created_at)
                            FROM public.daily_check_in AS check_in
                            WHERE check_in.student_id = s.id),
                           (SELECT max(login_session.authenticated_at)
                            FROM public.account_session AS login_session
                            JOIN public.account AS session_account
                              ON session_account.id = login_session.account_id
                            WHERE session_account.learner_id = s.id)
                       ) AS last_activity_at
                FROM public.student AS s
                LEFT JOIN public.account AS learner_account ON learner_account.learner_id = s.id
                WHERE COALESCE(learner_account.role, 'learner') <> 'administrator'
                  AND NOT s.is_test
            )
            SELECT count(*) AS learners,
                   count(*) FILTER (WHERE claimed_by IS NOT NULL) AS claimed_learners,
                   count(*) FILTER (WHERE claimed_by IS NULL) AS unclaimed_learners,
                   count(*) FILTER (WHERE last_activity_at >= now() - interval '7 days')
                       AS active_learners_7d
            FROM learner_activity
            """;

    private final JdbcClient jdbc;
    private final CoursewareScope coursewareScope;
    private final LearnerQuery learnerQuery;
    private final LearnerClaimAccess learnerClaims;
    private final Clock clock;
    private final ZoneId zone;

    AdminAnalyticsService(JdbcClient jdbc, CoursewareScope coursewareScope, LearnerQuery learnerQuery,
                          LearnerClaimAccess learnerClaims, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.coursewareScope = coursewareScope;
        this.learnerQuery = learnerQuery;
        this.learnerClaims = learnerClaims;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    Overview overview(long administratorAccountId) {
        LearnerCounts counts = jdbc.sql(LEARNER_ACTIVITY_SQL)
                .query((resultSet, rowNumber) -> new LearnerCounts(
                        resultSet.getLong("learners"),
                        resultSet.getLong("claimed_learners"),
                        resultSet.getLong("unclaimed_learners"),
                        resultSet.getLong("active_learners_7d")))
                .single();
        ReviewCounts reviews = visibleSubmissionCounts(administratorAccountId);
        CoursewareStats courseware = coursewareStats(administratorAccountId);
        return new Overview(
                counts.learners(),
                counts.claimedLearners(),
                counts.unclaimedLearners(),
                counts.activeLearners7d(),
                reviews.pendingReviews(),
                reviews.submissions7d(),
                courseware);
    }

    @Transactional(readOnly = true)
    Activity activity(long administratorAccountId, int days) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, DailyCount> counts = dailyCounts(administratorAccountId, start, today);
        List<ActivityPoint> points = new ArrayList<>(days);
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            DailyCount count = counts.getOrDefault(date, DailyCount.empty(date));
            points.add(new ActivityPoint(date, count.submissions(), count.reviews()));
        }
        return new Activity(days, points);
    }

    @Transactional(readOnly = true)
    LearnerProfile learnerProfile(long administratorAccountId, long learnerId) {
        switch (learnerClaims.state(learnerId, administratorAccountId)) {
            case LEARNER_NOT_FOUND -> throw new AnalyticsLearnerNotFound(learnerId);
            case UNCLAIMED, CLAIMED_BY_OTHER_ADMINISTRATOR ->
                    throw new AnalyticsLearnerNotClaimed(learnerId);
            case CLAIMED_BY_CURRENT_ADMINISTRATOR -> {
            }
        }
        LearnerQuery.Learner learner = learnerQuery.findById(learnerId)
                .orElseThrow(() -> new AnalyticsLearnerNotFound(learnerId));
        PointsRow points = points(learnerId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate activityStart = today.minusDays(PROFILE_ACTIVITY_DAYS - 1L);
        long checkInDays = countCheckInDays(learnerId, activityStart, today);
        List<SubjectProgress> subjectProgress = subjectProgress(administratorAccountId, learnerId);
        List<RecentSubmission> recentSubmissions =
                recentSubmissions(administratorAccountId, learnerId);
        Map<LocalDate, Long> dailySubmissions =
                learnerDailySubmissions(administratorAccountId, learnerId, activityStart, today);
        List<LearnerActivityPoint> activity = new ArrayList<>(PROFILE_ACTIVITY_DAYS);
        for (LocalDate date = activityStart; !date.isAfter(today); date = date.plusDays(1)) {
            activity.add(new LearnerActivityPoint(date, dailySubmissions.getOrDefault(date, 0L)));
        }
        long level = points.lifetimePoints() / POINTS_PER_LEVEL + 1;
        return new LearnerProfile(
                learner.learnerId(),
                learner.username(),
                learner.claimedByAccountId(),
                learner.claimedByUsername(),
                learner.lastActivityAt(),
                points.points(),
                points.lifetimePoints(),
                level,
                checkInDays,
                subjectProgress,
                recentSubmissions,
                activity);
    }

    private ReviewCounts visibleSubmissionCounts(long administratorAccountId) {
        return jdbc.sql("""
                SELECT count(*) FILTER (WHERE submission.review_status = 'pending') AS pending_reviews,
                       count(*) FILTER (WHERE submission.submitted_at >= now() - interval '7 days')
                           AS submissions_7d
                FROM public.article_question_submissions AS submission
                JOIN public.student AS learner
                  ON learner.id = submission.student_id
                 AND NOT learner.is_test
                WHERE %s
                """.formatted(coursewareScope.visibleArticlePredicate("submission.blog_id")))
                .param("administratorAccountId", administratorAccountId)
                .query((resultSet, rowNumber) -> new ReviewCounts(
                        resultSet.getLong("pending_reviews"),
                        resultSet.getLong("submissions_7d")))
                .single();
    }

    private CoursewareStats coursewareStats(long administratorAccountId) {
        String visiblePredicate = coursewareScope.visibleArticlePredicate("catalog.article_id");
        VisibleCourseware visible = jdbc.sql("""
                SELECT count(*) AS visible,
                       count(DISTINCT catalog.subject) AS subjects
                FROM public.courseware_catalog AS catalog
                WHERE %s
                """.formatted(visiblePredicate))
                .param("administratorAccountId", administratorAccountId)
                .query((resultSet, rowNumber) -> new VisibleCourseware(
                        resultSet.getLong("visible"),
                        resultSet.getLong("subjects")))
                .single();
        long unclaimed = jdbc.sql("""
                SELECT count(*)
                FROM public.courseware_catalog AS catalog
                WHERE NOT EXISTS (
                          SELECT 1
                          FROM public.admin_subject_claim AS subject_claim
                          WHERE subject_claim.subject = catalog.subject
                            AND NOT EXISTS (
                                SELECT 1
                                FROM public.admin_courseware_exclusion AS exclusion
                                WHERE exclusion.admin_account_id = subject_claim.admin_account_id
                                  AND exclusion.article_id = catalog.article_id
                            )
                      )
                  AND NOT EXISTS (
                          SELECT 1
                          FROM public.admin_courseware_claim AS courseware_claim
                          WHERE courseware_claim.article_id = catalog.article_id
                      )
                """)
                .query(Long.class)
                .single();
        return new CoursewareStats(visible.visible(), unclaimed, visible.subjects());
    }

    private Map<LocalDate, DailyCount> dailyCounts(long administratorAccountId, LocalDate start, LocalDate end) {
        List<DailyCount> rows = jdbc.sql("""
                SELECT activity_date,
                       count(*) FILTER (WHERE kind = 'submission') AS submissions,
                       count(*) FILTER (WHERE kind = 'review') AS reviews
                FROM (
                    SELECT (submission.submitted_at AT TIME ZONE CAST(:zone AS text))::date AS activity_date,
                           'submission' AS kind
                    FROM public.article_question_submissions AS submission
                    JOIN public.student AS learner
                      ON learner.id = submission.student_id
                     AND NOT learner.is_test
                    WHERE %s
                      AND (submission.submitted_at AT TIME ZONE CAST(:zone AS text))::date
                          BETWEEN :startDate AND :endDate
                    UNION ALL
                    SELECT (submission.reviewed_at AT TIME ZONE CAST(:zone AS text))::date AS activity_date,
                           'review' AS kind
                    FROM public.article_question_submissions AS submission
                    JOIN public.student AS learner
                      ON learner.id = submission.student_id
                     AND NOT learner.is_test
                    WHERE submission.review_status = 'reviewed'
                      AND %s
                      AND (submission.reviewed_at AT TIME ZONE CAST(:zone AS text))::date
                          BETWEEN :startDate AND :endDate
                ) AS daily
                GROUP BY activity_date
                ORDER BY activity_date
                """.formatted(
                        coursewareScope.visibleArticlePredicate("submission.blog_id"),
                        coursewareScope.visibleArticlePredicate("submission.blog_id")))
                .param("administratorAccountId", administratorAccountId)
                .param("zone", zone.getId())
                .param("startDate", start)
                .param("endDate", end)
                .query((resultSet, rowNumber) -> new DailyCount(
                        resultSet.getObject("activity_date", LocalDate.class),
                        resultSet.getLong("submissions"),
                        resultSet.getLong("reviews")))
                .list();
        Map<LocalDate, DailyCount> counts = new LinkedHashMap<>();
        for (DailyCount row : rows) {
            counts.put(row.date(), row);
        }
        return counts;
    }

    private PointsRow points(long learnerId) {
        return jdbc.sql("""
                SELECT points, lifetime_points
                FROM public.user_points
                WHERE student_id = :learnerId
                """)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) -> new PointsRow(
                        resultSet.getLong("points"),
                        resultSet.getLong("lifetime_points")))
                .optional()
                .orElse(new PointsRow(0, 0));
    }

    private long countCheckInDays(long learnerId, LocalDate start, LocalDate end) {
        return jdbc.sql("""
                SELECT count(DISTINCT check_in_date)
                FROM public.daily_check_in
                WHERE student_id = :learnerId
                  AND check_in_date BETWEEN :startDate AND :endDate
                """)
                .param("learnerId", learnerId)
                .param("startDate", start)
                .param("endDate", end)
                .query(Long.class)
                .single();
    }

    private List<SubjectProgress> subjectProgress(long administratorAccountId, long learnerId) {
        return jdbc.sql("""
                SELECT catalog.subject,
                       count(*) AS total_questions,
                       count(submission.id) AS submitted_questions,
                       count(*) FILTER (WHERE submission.review_status = 'pending') AS pending_reviews
                FROM public.article_answer_keys AS answer
                JOIN public.student AS learner
                  ON learner.id = :learnerId
                 AND answer.blog_id = ANY (learner.permissions::bigint[])
                JOIN public.courseware_catalog AS catalog
                  ON catalog.article_id = answer.blog_id
                LEFT JOIN public.article_question_submissions AS submission
                  ON submission.blog_id = answer.blog_id
                 AND submission.question_id = answer.question_id
                 AND submission.student_id = :learnerId
                WHERE %s
                GROUP BY catalog.subject
                ORDER BY catalog.subject
                """.formatted(coursewareScope.visibleArticlePredicate("catalog.article_id")))
                .param("administratorAccountId", administratorAccountId)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) -> new SubjectProgress(
                        resultSet.getString("subject"),
                        resultSet.getLong("total_questions"),
                        resultSet.getLong("submitted_questions"),
                        resultSet.getLong("pending_reviews")))
                .list();
    }

    private List<RecentSubmission> recentSubmissions(long administratorAccountId, long learnerId) {
        return jdbc.sql("""
                SELECT submission.blog_id,
                       submission.question_id,
                       submission.review_status,
                       submission.review_result,
                       submission.submitted_at
                FROM public.article_question_submissions AS submission
                WHERE submission.student_id = :learnerId
                  AND %s
                ORDER BY submission.submitted_at DESC, submission.id DESC
                LIMIT :limit
                """.formatted(coursewareScope.visibleArticlePredicate("submission.blog_id")))
                .param("administratorAccountId", administratorAccountId)
                .param("learnerId", learnerId)
                .param("limit", RECENT_SUBMISSION_LIMIT)
                .query(AdminAnalyticsService::mapRecentSubmission)
                .list();
    }

    private Map<LocalDate, Long> learnerDailySubmissions(long administratorAccountId, long learnerId,
                                                        LocalDate start, LocalDate end) {
        List<LearnerDailyCount> rows = jdbc.sql("""
                SELECT (submission.submitted_at AT TIME ZONE CAST(:zone AS text))::date AS activity_date,
                       count(*) AS submissions
                FROM public.article_question_submissions AS submission
                WHERE submission.student_id = :learnerId
                  AND (submission.submitted_at AT TIME ZONE CAST(:zone AS text))::date
                      BETWEEN :startDate AND :endDate
                  AND %s
                GROUP BY activity_date
                ORDER BY activity_date
                """.formatted(coursewareScope.visibleArticlePredicate("submission.blog_id")))
                .param("administratorAccountId", administratorAccountId)
                .param("learnerId", learnerId)
                .param("zone", zone.getId())
                .param("startDate", start)
                .param("endDate", end)
                .query((resultSet, rowNumber) -> new LearnerDailyCount(
                        resultSet.getObject("activity_date", LocalDate.class),
                        resultSet.getLong("submissions")))
                .list();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LearnerDailyCount row : rows) {
            counts.put(row.date(), row.submissions());
        }
        return counts;
    }

    private static RecentSubmission mapRecentSubmission(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new RecentSubmission(
                resultSet.getLong("blog_id"),
                resultSet.getString("question_id"),
                resultSet.getString("review_status"),
                resultSet.getString("review_result"),
                resultSet.getTimestamp("submitted_at").toInstant());
    }

    record Overview(
            long learners,
            long claimedLearners,
            long unclaimedLearners,
            long activeLearners7d,
            long pendingReviews,
            long submissions7d,
            CoursewareStats courseware) {
    }

    record CoursewareStats(long visible, long unclaimed, long subjects) {
    }

    record Activity(int days, List<ActivityPoint> points) {

        Activity {
            points = List.copyOf(points);
        }
    }

    record ActivityPoint(LocalDate date, long submissions, long reviews) {
    }

    record LearnerProfile(
            long learnerId,
            String username,
            Long claimedByAccountId,
            String claimedByUsername,
            Instant lastActivityAt,
            long points,
            long lifetimePoints,
            long level,
            long checkInDays30,
            List<SubjectProgress> subjectProgress,
            List<RecentSubmission> recentSubmissions,
            List<LearnerActivityPoint> activity) {

        LearnerProfile {
            subjectProgress = List.copyOf(subjectProgress);
            recentSubmissions = List.copyOf(recentSubmissions);
            activity = List.copyOf(activity);
        }
    }

    record SubjectProgress(String subject, long totalQuestions, long submittedQuestions,
                           long pendingReviews) {
    }

    record RecentSubmission(long articleId, String questionId, String reviewStatus,
                            String reviewResult, Instant submittedAt) {
    }

    record LearnerActivityPoint(LocalDate date, long submissions) {
    }

    private record LearnerCounts(long learners, long claimedLearners, long unclaimedLearners,
                                 long activeLearners7d) {
    }

    private record ReviewCounts(long pendingReviews, long submissions7d) {
    }

    private record VisibleCourseware(long visible, long subjects) {
    }

    private record PointsRow(long points, long lifetimePoints) {
    }

    private record DailyCount(LocalDate date, long submissions, long reviews) {

        static DailyCount empty(LocalDate date) {
            return new DailyCount(date, 0, 0);
        }
    }

    private record LearnerDailyCount(LocalDate date, long submissions) {
    }
}
