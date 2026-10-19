package com.tutorbase.growth;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tutorbase.growth.AchievementCatalog.Achievement;
import com.tutorbase.growth.AchievementCatalog.Metric;
import com.tutorbase.growth.AchievementCatalog.Tier;
import com.tutorbase.growth.TaskCatalog.Period;
import com.tutorbase.growth.TaskCatalog.Task;

/**
 * 鎴愰暱涓績锛氭垚灏憋紙鍒嗗眰銆侀鍙栫Н鍒嗭級涓庝换鍔★紙姣忔棩/姣忓懆鍒锋柊銆侀檺鏃舵帴鍙栵級銆? * 鎵€鏈夋潯浠跺垽鏂€佺Н鍒嗗彂鏀句笌棰嗗彇骞傜瓑閮藉湪鏈嶅姟绔簨鍔″唴瀹屾垚銆? */
@Service
class GrowthService {

    enum TaskStatus { AVAILABLE, IN_PROGRESS, CLAIMABLE, CLAIMED, EXPIRED }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    GrowthService(JdbcClient jdbc, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    // =====================================================================
    // 鎴愬氨
    // =====================================================================

    AchievementSummary achievements(long learnerId) {
        Set<String> claimedKeys = new HashSet<>(jdbc.sql("""
                SELECT achievement_key || ':' || tier_level
                FROM public.achievement_claims
                WHERE student_id = :studentId
                """).param("studentId", learnerId).query(String.class).list());

        List<AchievementView> views = AchievementCatalog.ALL.stream()
                .map(achievement -> achievementView(learnerId, achievement, claimedKeys))
                .toList();

        long earnedPoints = jdbc.sql("""
                SELECT COALESCE(SUM(reward_points), 0)
                FROM public.achievement_claims
                WHERE student_id = :studentId
                """).param("studentId", learnerId).query(Long.class).single();
        int claimedTiers = claimedKeys.size();
        int totalTiers = AchievementCatalog.ALL.stream().mapToInt(item -> item.tiers().size()).sum();
        long points = points(learnerId);
        return new AchievementSummary(points, earnedPoints, claimedTiers, totalTiers, views);
    }

    private AchievementView achievementView(long learnerId, Achievement achievement, Set<String> claimedKeys) {
        long current = totalMetric(learnerId, achievement.metric());
        List<TierView> tiers = achievement.tiers().stream()
                .map(tier -> {
                    boolean claimed = claimedKeys.contains(achievement.key() + ':' + tier.level());
                    String state = claimed ? "claimed" : current >= tier.requirement() ? "claimable" : "locked";
                    return new TierView(tier.level(), tier.requirement(), tier.rewardPoints(), state);
                })
                .toList();
        Integer claimableLevel = achievement.tiers().stream()
                .filter(tier -> !claimedKeys.contains(achievement.key() + ':' + tier.level())
                        && current >= tier.requirement())
                .map(Tier::level)
                .findFirst()
                .orElse(null);
        Tier next = achievement.tiers().stream()
                .filter(tier -> !claimedKeys.contains(achievement.key() + ':' + tier.level()))
                .findFirst()
                .orElse(null);
        return new AchievementView(achievement.key(), achievement.title(), achievement.description(),
                achievement.icon(), achievement.tone(), achievement.metric().name(), current, tiers,
                claimableLevel, next == null ? null : new TierView(next.level(), next.requirement(),
                        next.rewardPoints(), null));
    }

    @Transactional
    ClaimResult claimAchievement(long learnerId, String key) {
        Achievement achievement = AchievementCatalog.find(key).orElseThrow(InvalidGrowthKey::new);
        Set<Integer> claimedLevels = new HashSet<>(jdbc.sql("""
                SELECT tier_level FROM public.achievement_claims
                WHERE student_id = :studentId AND achievement_key = :achievementKey
                """).param("studentId", learnerId).param("achievementKey", key)
                .query(Integer.class).list());
        long current = totalMetric(learnerId, achievement.metric());
        Optional<Tier> candidate = achievement.tiers().stream()
                .filter(tier -> !claimedLevels.contains(tier.level()) && current >= tier.requirement())
                .findFirst();
        Tier tier = candidate.orElseThrow(AchievementNotClaimable::new);

        Optional<Long> inserted;
        try {
            inserted = jdbc.sql("""
                    INSERT INTO public.achievement_claims
                        (student_id, achievement_key, tier_level, reward_points)
                    VALUES (:studentId, :achievementKey, :tierLevel, :rewardPoints)
                    ON CONFLICT (student_id, achievement_key, tier_level) DO NOTHING
                    RETURNING id
                    """)
                    .param("studentId", learnerId)
                    .param("achievementKey", key)
                    .param("tierLevel", tier.level())
                    .param("rewardPoints", tier.rewardPoints())
                    .query(Long.class)
                    .optional();
        } catch (DuplicateKeyException exception) {
            throw new AchievementAlreadyClaimed();
        }
        if (inserted.isEmpty()) {
            throw new AchievementAlreadyClaimed();
        }
        long totalPoints = awardPoints(learnerId, tier.rewardPoints());
        return new ClaimResult(achievement.key(), tier.level(), tier.rewardPoints(), totalPoints);
    }

    // =====================================================================
    // 浠诲姟
    // =====================================================================

    TaskBoard taskBoard(long learnerId) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        List<TaskView> daily = new java.util.ArrayList<>();
        List<TaskView> weekly = new java.util.ArrayList<>();
        List<TaskView> limited = new java.util.ArrayList<>();
        for (Task task : TaskCatalog.ALL) {
            TaskView view = taskView(learnerId, task, now);
            switch (task.period()) {
                case DAILY -> daily.add(view);
                case WEEKLY -> weekly.add(view);
                case LIMITED -> limited.add(view);
            }
        }
        return new TaskBoard(points(learnerId), daily, weekly, limited,
                nextDayStart(now).toInstant(), nextWeekStart(now).toInstant());
    }

    private TaskView taskView(long learnerId, Task task, ZonedDateTime now) {
        if (task.period() != Period.LIMITED) {
            String periodKey = periodKey(task.period(), now);
            boolean claimed = taskClaimed(learnerId, task.key(), periodKey);
            OffsetDateTime periodStart = (task.period() == Period.DAILY ? dayStart(now) : weekStart(now))
                    .toOffsetDateTime();
            long progress = windowMetric(learnerId, task.metric(), periodStart, now.toOffsetDateTime());
            TaskStatus status = claimed ? TaskStatus.CLAIMED
                    : progress >= task.target() ? TaskStatus.CLAIMABLE : TaskStatus.IN_PROGRESS;
            return new TaskView(task.key(), task.period().name().toLowerCase(), task.title(), task.description(),
                    task.icon(), task.tone(), task.rewardPoints(), task.target(), (int) Math.min(progress, task.target()),
                    status.name().toLowerCase(), periodKey, null, null);
        }

        Optional<Assignment> assignment = assignment(learnerId, task.key());
        if (assignment.isEmpty()) {
            return new TaskView(task.key(), "limited", task.title(), task.description(), task.icon(), task.tone(),
                    task.rewardPoints(), task.target(), 0, "available", null, null, null);
        }
        Assignment current = assignment.get();
        long progress = windowMetric(learnerId, task.metric(), current.acceptedAt(), now.toOffsetDateTime());
        TaskStatus status;
        if (current.claimed()) {
            status = TaskStatus.CLAIMED;
        } else if (now.toInstant().isAfter(current.deadlineAt().toInstant())) {
            status = TaskStatus.EXPIRED;
        } else if (progress >= task.target()) {
            status = TaskStatus.CLAIMABLE;
        } else {
            status = TaskStatus.IN_PROGRESS;
        }
        return new TaskView(task.key(), "limited", task.title(), task.description(), task.icon(), task.tone(),
                task.rewardPoints(), task.target(), (int) Math.min(progress, task.target()),
                status.name().toLowerCase(), null, current.acceptedAt().toInstant(), current.deadlineAt().toInstant());
    }

    @Transactional
    void acceptTask(long learnerId, String key) {
        Task task = requireLimitedTask(key);
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        OffsetDateTime acceptedAt = now.toOffsetDateTime();
        OffsetDateTime deadline = now.plusDays(task.acceptWindowDays()).toOffsetDateTime();
        Optional<Assignment> existing = assignmentForUpdate(learnerId, key);
        if (existing.isPresent()) {
            Assignment assignment = existing.get();
            if (assignment.claimed()) {
                throw new TaskAlreadyClaimed();
            }
            if (!now.toInstant().isAfter(assignment.deadlineAt().toInstant())) {
                throw new TaskAlreadyAccepted();
            }
            jdbc.sql("""
                    UPDATE public.task_assignments
                    SET accepted_at = :acceptedAt, deadline_at = :deadlineAt, claimed_at = NULL
                    WHERE id = :id
                    """).param("acceptedAt", acceptedAt).param("deadlineAt", deadline)
                    .param("id", assignment.id()).update();
            return;
        }
        jdbc.sql("""
                INSERT INTO public.task_assignments (student_id, task_key, accepted_at, deadline_at)
                VALUES (:studentId, :taskKey, :acceptedAt, :deadlineAt)
                """).param("studentId", learnerId).param("taskKey", key)
                .param("acceptedAt", acceptedAt).param("deadlineAt", deadline).update();
    }

    @Transactional
    ClaimResult claimTask(long learnerId, String key) {
        Task task = TaskCatalog.find(key).orElseThrow(InvalidGrowthKey::new);
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        String periodKey;
        if (task.period() != Period.LIMITED) {
            periodKey = periodKey(task.period(), now);
            if (taskClaimed(learnerId, key, periodKey)) {
                throw new TaskAlreadyClaimed();
            }
            OffsetDateTime periodStart = (task.period() == Period.DAILY ? dayStart(now) : weekStart(now))
                    .toOffsetDateTime();
            long progress = windowMetric(learnerId, task.metric(), periodStart, now.toOffsetDateTime());
            if (progress < task.target()) {
                throw new TaskNotCompleted();
            }
        } else {
            Assignment assignment = assignmentForUpdate(learnerId, key).orElseThrow(TaskNotAccepted::new);
            if (assignment.claimed()) {
                throw new TaskAlreadyClaimed();
            }
            if (now.toInstant().isAfter(assignment.deadlineAt().toInstant())) {
                throw new TaskExpired();
            }
            long progress = windowMetric(learnerId, task.metric(), assignment.acceptedAt(),
                    now.toOffsetDateTime());
            if (progress < task.target()) {
                throw new TaskNotCompleted();
            }
            periodKey = "assignment:" + assignment.id();
        }

        try {
            int inserted = jdbc.sql("""
                    INSERT INTO public.task_claims
                        (student_id, task_key, period_key, reward_points)
                    VALUES (:studentId, :taskKey, :periodKey, :rewardPoints)
                    ON CONFLICT (student_id, task_key, period_key) WHERE task_key IS NOT NULL
                    DO NOTHING
                    """).param("studentId", learnerId).param("taskKey", key)
                    .param("periodKey", periodKey).param("rewardPoints", task.rewardPoints())
                    .update();
            if (inserted == 0) {
                throw new TaskAlreadyClaimed();
            }
        } catch (DuplicateKeyException exception) {
            throw new TaskAlreadyClaimed();
        }

        if (task.period() == Period.LIMITED) {
            jdbc.sql("""
                    UPDATE public.task_assignments SET claimed_at = now()
                    WHERE student_id = :studentId AND task_key = :taskKey AND claimed_at IS NULL
                    """).param("studentId", learnerId).param("taskKey", key).update();
        }
        long totalPoints = awardPoints(learnerId, task.rewardPoints());
        return new ClaimResult(key, null, task.rewardPoints(), totalPoints);
    }

    // =====================================================================
    // 鎸囨爣涓庢椂闂?    // =====================================================================

    long totalMetric(long learnerId, Metric metric) {
        return switch (metric) {
            case CHECKIN -> count("SELECT COUNT(*) FROM public.daily_check_in WHERE student_id = :studentId",
                    learnerId);
            case SUBMISSION -> count("""
                    SELECT COUNT(*) FROM public.article_question_submissions WHERE student_id = :studentId
                    """, learnerId);
            case WRONG_COLLECTED -> count("""
                    SELECT COUNT(*) FROM public.wrong_questions
                    WHERE student_id = :studentId AND removed = false
                    """, learnerId);
            case MASTERED -> count("""
                    SELECT COUNT(*) FROM public.wrong_questions
                    WHERE student_id = :studentId AND mastered = true AND removed = false
                    """, learnerId);
            case READING -> count("""
                    SELECT COUNT(*) FROM (
                        SELECT keyed.blog_id
                        FROM (SELECT DISTINCT blog_id FROM public.article_answer_keys) keyed
                        WHERE (SELECT COUNT(*) FROM public.article_question_submissions submissions
                               WHERE submissions.student_id = :studentId
                                 AND submissions.blog_id = keyed.blog_id)
                              >= (SELECT COUNT(*) FROM public.article_answer_keys answers
                                  WHERE answers.blog_id = keyed.blog_id)
                    ) completed
                    """, learnerId);
            case CARDS -> count("""
                    SELECT COUNT(*) FROM public.card_collection WHERE student_id = :studentId
                    """, learnerId);
        };
    }

    long windowMetric(long learnerId, TaskCatalog.Metric metric, OffsetDateTime since, OffsetDateTime until) {
        Map<String, Object> params = new HashMap<>();
        params.put("studentId", learnerId);
        params.put("since", since);
        params.put("until", until);
        params.put("sinceDate", since.toLocalDate());
        params.put("untilDate", until.toLocalDate());
        String sql = switch (metric) {
            case CHECKIN -> """
                    SELECT COUNT(*) FROM public.daily_check_in
                    WHERE student_id = :studentId AND check_in_date >= :sinceDate AND check_in_date <= :untilDate
                    """;
            case SUBMISSION -> """
                    SELECT COUNT(*) FROM public.article_question_submissions
                    WHERE student_id = :studentId AND submitted_at >= :since AND submitted_at < :until
                    """;
            case WRONG_COLLECTED -> """
                    SELECT COUNT(*) FROM public.wrong_questions
                    WHERE student_id = :studentId AND removed = false
                      AND created_at >= :since AND created_at < :until
                    """;
            case WRONG_MASTERED -> """
                    SELECT COUNT(*) FROM public.wrong_questions
                    WHERE student_id = :studentId AND mastered = true AND removed = false
                      AND updated_at >= :since AND updated_at < :until
                    """;
        };
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private long count(String sql, long learnerId) {
        return jdbc.sql(sql).param("studentId", learnerId).query(Long.class).single();
    }

    private long points(long learnerId) {
        return jdbc.sql("SELECT points FROM public.user_points WHERE student_id = :studentId")
                .param("studentId", learnerId).query(Long.class).optional().orElse(0L);
    }

    private long awardPoints(long learnerId, int amount) {
        return jdbc.sql("""
                INSERT INTO public.user_points (student_id, points, updated_at)
                VALUES (:studentId, :amount, now())
                ON CONFLICT (student_id) DO UPDATE
                    SET points = public.user_points.points + EXCLUDED.points,
                        updated_at = now()
                RETURNING points
                """).param("studentId", learnerId).param("amount", amount)
                .query(Long.class).single();
    }

    private Task requireLimitedTask(String key) {
        Task task = TaskCatalog.find(key).orElseThrow(InvalidGrowthKey::new);
        if (task.period() != Period.LIMITED) {
            throw new InvalidGrowthKey();
        }
        return task;
    }

    private boolean taskClaimed(long learnerId, String key, String periodKey) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM public.task_claims
                WHERE student_id = :studentId AND task_key = :taskKey AND period_key = :periodKey
                """).param("studentId", learnerId).param("taskKey", key).param("periodKey", periodKey)
                .query(Long.class).single() > 0;
    }

    private Optional<Assignment> assignment(long learnerId, String key) {
        return jdbc.sql("""
                SELECT id, accepted_at, deadline_at, claimed_at
                FROM public.task_assignments
                WHERE student_id = :studentId AND task_key = :taskKey
                """).param("studentId", learnerId).param("taskKey", key)
                .query((resultSet, rowNumber) -> new Assignment(
                        resultSet.getLong("id"),
                        resultSet.getObject("accepted_at", OffsetDateTime.class),
                        resultSet.getObject("deadline_at", OffsetDateTime.class),
                        resultSet.getObject("claimed_at") != null))
                .optional();
    }

    private Optional<Assignment> assignmentForUpdate(long learnerId, String key) {
        return jdbc.sql("""
                SELECT id, accepted_at, deadline_at, claimed_at
                FROM public.task_assignments
                WHERE student_id = :studentId AND task_key = :taskKey
                FOR UPDATE
                """).param("studentId", learnerId).param("taskKey", key)
                .query((resultSet, rowNumber) -> new Assignment(
                        resultSet.getLong("id"),
                        resultSet.getObject("accepted_at", OffsetDateTime.class),
                        resultSet.getObject("deadline_at", OffsetDateTime.class),
                        resultSet.getObject("claimed_at") != null))
                .optional();
    }

    private ZonedDateTime dayStart(ZonedDateTime now) {
        return now.toLocalDate().atStartOfDay(zone);
    }

    private ZonedDateTime nextDayStart(ZonedDateTime now) {
        return now.toLocalDate().plusDays(1).atStartOfDay(zone);
    }

    private ZonedDateTime weekStart(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        return monday.atStartOfDay(zone);
    }

    private ZonedDateTime nextWeekStart(ZonedDateTime now) {
        return weekStart(now).plusWeeks(1);
    }

    private String periodKey(Period period, ZonedDateTime now) {
        if (period == Period.DAILY) {
            return now.toLocalDate().toString();
        }
        WeekFields fields = WeekFields.ISO;
        int week = now.get(fields.weekOfWeekBasedYear());
        int year = now.get(fields.weekBasedYear());
        return "%d-W%02d".formatted(year, week);
    }

    // =====================================================================
    // Records
    // =====================================================================

    record TierView(int level, int requirement, int rewardPoints, String state) {
    }

    record AchievementView(String key, String title, String description, String icon, String tone, String metric,
                           long currentValue, List<TierView> tiers, Integer claimableLevel, TierView nextTier) {
    }

    record AchievementSummary(long points, long earnedPoints, int claimedTiers, int totalTiers,
                              List<AchievementView> achievements) {
    }

    record ClaimResult(String key, Integer tierLevel, long awardedPoints, long totalPoints) {
    }

    record TaskView(String key, String type, String title, String description, String icon, String tone,
                    int rewardPoints, int target, int progress, String status, String periodKey,
                    java.time.Instant acceptedAt, java.time.Instant deadlineAt) {
    }

    record TaskBoard(long points, List<TaskView> daily, List<TaskView> weekly, List<TaskView> limited,
                     java.time.Instant dailyResetAt, java.time.Instant weeklyResetAt) {
    }

    private record Assignment(long id, OffsetDateTime acceptedAt, OffsetDateTime deadlineAt, boolean claimed) {
    }
}
