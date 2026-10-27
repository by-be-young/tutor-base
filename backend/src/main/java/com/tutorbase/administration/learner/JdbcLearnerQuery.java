package com.tutorbase.administration.learner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class JdbcLearnerQuery implements LearnerQuery {

    private static final String FIND_AFTER_SQL = LearnerSql.SELECT + """
            LEFT JOIN public.account AS self_account ON self_account.learner_id = s.id
            WHERE s.id > :afterId
              AND COALESCE(self_account.role, 'learner') <> 'administrator'
            ORDER BY s.id ASC
            LIMIT :fetchLimit
            """;

    private static final String FIND_BY_ID_SQL = LearnerSql.SELECT + """
            WHERE s.id = :learnerId
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
                LearnerSql::map);

        boolean hasNextPage = rows.size() > limit;
        List<Learner> items = hasNextPage ? new ArrayList<>(rows.subList(0, limit)) : rows;
        OptionalLong nextAfterId = hasNextPage
                ? OptionalLong.of(items.getLast().learnerId())
                : OptionalLong.empty();
        return new LearnerPage(items, nextAfterId);
    }

    @Override
    public Optional<Learner> findById(long learnerId) {
        return jdbc.query(FIND_BY_ID_SQL, Map.of("learnerId", learnerId), LearnerSql::map)
                .stream()
                .findFirst();
    }
}
