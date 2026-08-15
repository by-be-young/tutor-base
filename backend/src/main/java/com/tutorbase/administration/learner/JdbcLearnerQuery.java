package com.tutorbase.administration.learner;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class JdbcLearnerQuery implements LearnerQuery {

    private static final String FIND_AFTER_SQL = """
            SELECT id, username, permissions
            FROM public.student
            WHERE id > :afterId
            ORDER BY id ASC
            LIMIT :fetchLimit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcLearnerQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LearnerPage findAfter(OptionalLong afterId, int limit) {
        List<Learner> rows = jdbc.query(
                FIND_AFTER_SQL,
                Map.of("afterId", afterId.orElse(0), "fetchLimit", limit + 1),
                JdbcLearnerQuery::mapLearner);

        boolean hasNextPage = rows.size() > limit;
        List<Learner> items = hasNextPage ? new ArrayList<>(rows.subList(0, limit)) : rows;
        OptionalLong nextAfterId = hasNextPage
                ? OptionalLong.of(items.getLast().learnerId())
                : OptionalLong.empty();
        return new LearnerPage(items, nextAfterId);
    }

    private static Learner mapLearner(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Learner(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                readArticleIds(resultSet.getArray("permissions")));
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
