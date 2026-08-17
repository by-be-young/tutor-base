package com.tutorbase.learning.access;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class JdbcContentGrantQuery implements ContentGrantQuery {

    private final JdbcClient jdbc;

    JdbcContentGrantQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<List<Long>> findArticleIds(long learnerId) {
        return jdbc.sql("SELECT permissions FROM public.student WHERE id = :learnerId")
                .param("learnerId", learnerId)
                .query(JdbcContentGrantQuery::mapArticleIds)
                .optional();
    }

    private static List<Long> mapArticleIds(ResultSet resultSet, int rowNumber) throws SQLException {
        Array permissions = resultSet.getArray("permissions");
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
                    .distinct()
                    .sorted()
                    .toList();
        } finally {
            permissions.free();
        }
    }
}
