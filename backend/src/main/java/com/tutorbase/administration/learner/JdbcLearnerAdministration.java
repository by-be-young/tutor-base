package com.tutorbase.administration.learner;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcLearnerAdministration implements LearnerAdministration {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearnerAdministration(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public long create(String username, long creatorAccountId) {
        String trimmedUsername = username.strip();
        try {
            long learnerId = jdbc.sql("""
                    INSERT INTO public.student (username, permissions, claimed_by, claimed_at)
                    VALUES (:username, '{}'::integer[], :creatorAccountId, now())
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
    public void replaceContentGrants(long learnerId, List<Long> articleIds) {
        Integer[] values = articleIds.stream()
                .distinct()
                .sorted()
                .map(Math::toIntExact)
                .toArray(Integer[]::new);
        int changed = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "UPDATE public.student SET permissions = ? WHERE id = ?");
            Array permissions = connection.createArrayOf("integer", values);
            statement.setArray(1, permissions);
            statement.setLong(2, learnerId);
            return statement;
        });
        if (changed == 0) {
            throw new LearnerNotFound(learnerId);
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
        if (state.claimedByAccountId() != null && state.claimedByAccountId() != administratorAccountId) {
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
                SELECT claimed_by
                FROM public.student
                WHERE id = :learnerId
                FOR UPDATE
                """)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) -> new ClaimState(resultSet.getObject("claimed_by", Long.class)))
                .optional()
                .orElseThrow(() -> new LearnerNotFound(learnerId));
    }

    private record ClaimState(Long claimedByAccountId) {
    }
}
