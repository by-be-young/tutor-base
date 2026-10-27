package com.tutorbase.administration.learner;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * PostgreSQL projection shared by the learner list and the single-learner lookup.
 *
 * <p>{@code last_activity_at} is the latest of the learner's own learning updates, wrong-question
 * updates, check-ins and authenticated sessions. {@code GREATEST} ignores {@code NULL} arguments and
 * yields {@code NULL} when every candidate is {@code NULL}.
 */
final class LearnerSql {

    private LearnerSql() {
    }

    static final String SELECT = """
            SELECT s.id,
                   s.username,
                   s.permissions,
                   s.claimed_by,
                   claimer.username AS claimed_by_username,
                   s.claimed_at,
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
            LEFT JOIN public.account AS claimer ON claimer.id = s.claimed_by
            """;

    static LearnerQuery.Learner map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LearnerQuery.Learner(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                readArticleIds(resultSet.getArray("permissions")),
                resultSet.getObject("claimed_by", Long.class),
                resultSet.getString("claimed_by_username"),
                toInstant(resultSet.getTimestamp("claimed_at")),
                toInstant(resultSet.getTimestamp("last_activity_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
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
}
