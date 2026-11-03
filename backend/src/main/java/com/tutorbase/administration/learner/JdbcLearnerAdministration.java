package com.tutorbase.administration.learner;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.tutorbase.administration.courseware.CoursewareScope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcLearnerAdministration implements LearnerAdministration {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final CoursewareScope coursewareScope;

    public JdbcLearnerAdministration(
            JdbcClient jdbc, JdbcTemplate jdbcTemplate, CoursewareScope coursewareScope) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.coursewareScope = coursewareScope;
    }

    @Override
    @Transactional
    public long create(String username, long creatorAccountId) {
        String trimmedUsername = username.strip();
        try {
            long learnerId = jdbc.sql("""
                    INSERT INTO public.student (username, permissions, claimed_by, claimed_at, is_test)
                    VALUES (:username, '{}'::integer[], :creatorAccountId, now(), true)
                    RETURNING id
                    """)
                    .param("username", trimmedUsername)
                    .param("creatorAccountId", creatorAccountId)
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                    INSERT INTO public.account (learner_id, username, username_normalized, role)
                    VALUES (:learnerId, :username, :normalizedUsername, 'learner')
                    """)
                    .param("learnerId", learnerId)
                    .param("username", trimmedUsername)
                    .param("normalizedUsername", trimmedUsername.toLowerCase(Locale.ROOT))
                    .update();
            return learnerId;
        } catch (DataIntegrityViolationException exception) {
            throw new LearnerUsernameConflict(trimmedUsername, exception);
        }
    }

    @Override
    @Transactional
    public void replaceContentGrants(
            long learnerId,
            long administratorAccountId,
            List<Long> articleIds,
            List<Long> removedArticleIds,
            List<String> enrolledSubjects,
            List<String> removedSubjects) {
        // 增量授权：请求里的文章/科目与既有授权取并集，只移除显式列在 removed* 里的项。
        // 这样“再授权一篇”不会把学员原有的授权冲掉；删除必须由调用方明确声明。
        Set<Long> visible = Set.copyOf(coursewareScope.visibleArticleIds(administratorAccountId));
        List<Long> requested = articleIds == null
                ? List.of()
                : articleIds.stream().distinct().sorted().toList();
        Set<Long> requestedRemovals = removedArticleIds == null
                ? Set.of()
                : removedArticleIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        ContentState state = lockContentState(learnerId);
        // 只有本管理员范围内的授权允许移除；范围外（别人的授权）静默保留，避免越权删除。
        Set<Long> removals = requestedRemovals.stream().filter(visible::contains)
                .collect(java.util.stream.Collectors.toSet());
        for (Long articleId : requested) {
            // 已授权过的文章可能是其他管理员的授权，原样保留，不再按本管理员范围校验；
            // 只有“新增”授权才必须落在本管理员的“我的课件”范围内。
            if (!state.articleIds().contains(articleId) && !visible.contains(articleId)) {
                throw new LearnerCoursewareNotClaimed(articleId);
            }
        }
        TreeSet<String> requestedSubjects = normalizeSubjects(enrolledSubjects);
        TreeSet<String> removedSubjectNames = normalizeSubjects(removedSubjects);
        // 同理：范围外的科目（别人的开通记录）保留，不因本管理员的请求被取消。
        removedSubjectNames.removeIf(subject -> !isVisibleSubject(administratorAccountId, subject));
        Set<String> existingSubjects = Set.copyOf(state.subjects());
        for (String subject : requestedSubjects) {
            if (!existingSubjects.contains(subject)) {
                requireVisibleSubject(administratorAccountId, subject);
            }
        }
        TreeSet<Long> finalArticleIds = new TreeSet<>(state.articleIds());
        finalArticleIds.addAll(requested);
        finalArticleIds.removeAll(removals);
        TreeSet<String> finalSubjects = new TreeSet<>(existingSubjects);
        finalSubjects.addAll(requestedSubjects);
        finalSubjects.removeAll(removedSubjectNames);
        Integer[] articleValues = finalArticleIds.stream()
                .map(Math::toIntExact)
                .toArray(Integer[]::new);
        String[] subjectValues = finalSubjects.toArray(String[]::new);
        int changed = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "UPDATE public.student SET permissions = ?, subjects = ? WHERE id = ?");
            Array permissions = connection.createArrayOf("integer", articleValues);
            Array subjects = connection.createArrayOf("text", subjectValues);
            statement.setArray(1, permissions);
            statement.setArray(2, subjects);
            statement.setLong(3, learnerId);
            return statement;
        });
        if (changed == 0) {
            throw new LearnerNotFound(learnerId);
        }
    }

    private ContentState lockContentState(long learnerId) {
        return jdbc.sql("""
                SELECT permissions, subjects
                FROM public.student
                WHERE id = :learnerId
                FOR UPDATE
                """)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) -> new ContentState(
                        readArticleIds(resultSet.getArray("permissions")),
                        readTextValues(resultSet.getArray("subjects"))))
                .optional()
                .orElseThrow(() -> new LearnerNotFound(learnerId));
    }

    private static TreeSet<String> normalizeSubjects(List<String> subjects) {
        TreeSet<String> normalized = new TreeSet<>();
        if (subjects == null) {
            return normalized;
        }
        for (String subject : subjects) {
            String value = subject == null ? "" : subject.strip();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private boolean isVisibleSubject(long administratorAccountId, String subject) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.courseware_catalog AS subject_catalog
                    WHERE subject_catalog.subject = :subject
                      AND %s
                )
                """.formatted(coursewareScope.visibleArticlePredicate("subject_catalog.article_id")))
                .param("administratorAccountId", administratorAccountId)
                .param("subject", subject)
                .query(Boolean.class)
                .single();
    }

    private void requireVisibleSubject(long administratorAccountId, String subject) {
        if (!isVisibleSubject(administratorAccountId, subject)) {
            throw new LearnerSubjectNotClaimed(subject);
        }
    }

    private static List<String> readTextValues(Array values) throws SQLException {
        if (values == null) {
            return List.of();
        }
        try {
            Object value = values.getArray();
            if (!(value instanceof Object[] items)) {
                throw new SQLException("Unsupported PostgreSQL array representation");
            }
            return Arrays.stream(items)
                    .map(String.class::cast)
                    .toList();
        } finally {
            values.free();
        }
    }

    private static List<Long> readArticleIds(Array permissions) throws SQLException {
        if (permissions == null) {
            return List.of();
        }
        try {
            Object value = permissions.getArray();
            if (!(value instanceof Object[] values)) {
                throw new SQLException("Unsupported PostgreSQL array representation");
            }
            return Arrays.stream(values)
                    .map(Number.class::cast)
                    .map(Number::longValue)
                    .sorted()
                    .toList();
        } finally {
            permissions.free();
        }
    }

    @Override
    @Transactional
    public void claim(long learnerId, long administratorAccountId) {
        ClaimState state = lockClaim(learnerId);
        if (state.claimedByAccountId() != null && state.claimedByAccountId() != administratorAccountId) {
            throw new LearnerAlreadyClaimed(learnerId);
        }
        jdbc.sql("""
                UPDATE public.student
                SET claimed_by = :administratorAccountId,
                    claimed_at = COALESCE(claimed_at, now())
                WHERE id = :learnerId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("learnerId", learnerId)
                .update();
    }

    @Override
    @Transactional
    public void release(long learnerId, long administratorAccountId) {
        ClaimState state = lockClaim(learnerId);
        if (state.claimedByAccountId() == null) {
            return;
        }
        if (state.claimedByAccountId() != administratorAccountId) {
            throw new LearnerAlreadyClaimed(learnerId);
        }
        jdbc.sql("""
                UPDATE public.student
                SET claimed_by = NULL,
                    claimed_at = NULL
                WHERE id = :learnerId
                """)
                .param("learnerId", learnerId)
                .update();
    }

    @Override
    @Transactional
    public void deleteLearner(long learnerId, long administratorAccountId) {
        ClaimState state = lockClaim(learnerId);
        if (state.specialTest()) {
            throw new SpecialTestLearnerProtected(learnerId);
        }
        if (state.claimedByAccountId() == null) {
            throw new LearnerNotClaimed(learnerId);
        }
        if (state.claimedByAccountId() != administratorAccountId) {
            throw new LearnerAlreadyClaimed(learnerId);
        }
        boolean protectedAdministrator = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.account
                    WHERE learner_id = :learnerId
                      AND role = 'administrator'
                )
                """)
                .param("learnerId", learnerId)
                .query(Boolean.class)
                .single();
        if (protectedAdministrator) {
            throw new AdministratorLearnerProtected(learnerId);
        }
        // account.learner_id 使用 ON DELETE RESTRICT，必须先删除账户再删除学员行；
        // 会话和激活码随账户级联删除，作答/错题/签到/积分/卡片/任务/成就随学员级联删除。
        jdbc.sql("DELETE FROM public.account WHERE learner_id = :learnerId")
                .param("learnerId", learnerId)
                .update();
        jdbc.sql("DELETE FROM public.student WHERE id = :learnerId")
                .param("learnerId", learnerId)
                .update();
    }

    private ClaimState lockClaim(long learnerId) {
        return jdbc.sql("""
                SELECT claimed_by, is_special_test
                FROM public.student
                WHERE id = :learnerId
                FOR UPDATE
                """)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) -> new ClaimState(
                        resultSet.getObject("claimed_by", Long.class),
                        resultSet.getBoolean("is_special_test")))
                .optional()
                .orElseThrow(() -> new LearnerNotFound(learnerId));
    }

    private record ClaimState(Long claimedByAccountId, boolean specialTest) {
    }

    private record ContentState(List<Long> articleIds, List<String> subjects) {
    }
}
