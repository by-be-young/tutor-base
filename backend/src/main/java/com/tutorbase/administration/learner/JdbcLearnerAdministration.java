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
    public long create(String username) {
        String trimmedUsername = username.strip();
        try {
            long learnerId = jdbc.sql("""
                    INSERT INTO public.student (username, permissions)
                    VALUES (:username, '{}'::integer[])
                    RETURNING id
                    """)
                    .param("username", trimmedUsername)
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
}
