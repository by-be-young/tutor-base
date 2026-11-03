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
        // 特殊测试学员拥有全部课件权限：直接返回 courseware_catalog 全部 article_id；
        // catalog 为空时回退到显式 permissions。普通学员仍只返回显式授权。
        return jdbc.sql("""
                SELECT CASE
                           WHEN learner.is_special_test THEN COALESCE(
                               (SELECT array_agg(DISTINCT catalog.article_id::integer
                                                 ORDER BY catalog.article_id::integer)
                                FROM public.courseware_catalog AS catalog),
                               learner.permissions)
                           ELSE learner.permissions
                       END AS permissions
                FROM public.student AS learner
                WHERE learner.id = :learnerId
                """)
                .param("learnerId", learnerId)
                .query(JdbcContentGrantQuery::mapArticleIds)
                .optional();
    }

    @Override
    public Optional<Boolean> isSpecialTestLearner(long learnerId) {
        return jdbc.sql("""
                SELECT is_special_test
                FROM public.student
                WHERE id = :learnerId
                """)
                .param("learnerId", learnerId)
                .query(Boolean.class)
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
