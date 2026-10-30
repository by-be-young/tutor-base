package com.tutorbase.administration.learner;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
            long learnerId, long administratorAccountId, List<Long> articleIds, List<String> enrolledSubjects) {
        Set<Long> visible = Set.copyOf(coursewareScope.visibleArticleIds(administratorAccountId));
        List<Long> requested = articleIds.stream()
                .distinct()
                .sorted()
                .toList();
        for (Long articleId : requested) {
            if (!visible.contains(articleId)) {
                throw new LearnerCoursewareNotClaimed(articleId);
            }
        }
        TreeSet<String> requestedSubjects = new TreeSet<>();
        if (enrolledSubjects != null) {
            for (String subject : enrolledSubjects) {
                String normalized = subject.strip();
                if (!normalized.isEmpty()) {
                    requestedSubjects.add(normalized);
                }
            }
            for (String subject : requestedSubjects) {
                requireVisibleSubject(administratorAccountId, subject);
            }
        }
        ContentState state = lockContentState(learnerId);
        TreeSet<Long> finalArticleIds = new TreeSet<>();
        state.articleIds().stream()
                .filter(articleId -> !visible.contains(articleId))
                .forEach(finalArticleIds::add);
        finalArticleIds.addAll(requested);
        Integer[] articleValues = finalArticleIds.stream()
                .map(Math::toIntExact)
                .toArray(Integer[]::new);
        String[] subjectValues = (enrolledSubjects == null
                ? state.subjects()
                : List.copyOf(requestedSubjects))
                .toArray(String[]::new);
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

    private void requireVisibleSubject(long administratorAccountId, String subject) {
        boolean visible = jdbc.sql("""
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
        if (!visible) {
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
