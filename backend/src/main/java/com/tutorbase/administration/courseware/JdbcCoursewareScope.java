package com.tutorbase.administration.courseware;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class JdbcCoursewareScope implements CoursewareScope {

    private static final String VISIBLE_PREDICATE = """
            EXISTS (
                SELECT 1
                FROM public.courseware_catalog AS scoped_catalog
                WHERE scoped_catalog.article_id = %s
                  AND (
                      EXISTS (
                          SELECT 1
                          FROM public.admin_subject_claim AS scoped_subject_claim
                          WHERE scoped_subject_claim.admin_account_id = :administratorAccountId
                            AND scoped_subject_claim.subject = scoped_catalog.subject
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM public.admin_courseware_claim AS scoped_courseware_claim
                          WHERE scoped_courseware_claim.admin_account_id = :administratorAccountId
                            AND scoped_courseware_claim.article_id = scoped_catalog.article_id
                      )
                  )
            )""";

    private final JdbcClient jdbc;

    JdbcCoursewareScope(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isVisible(long administratorAccountId, long articleId) {
        return jdbc.sql("SELECT " + visibleArticlePredicate(":articleId"))
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<Long> visibleArticleIds(long administratorAccountId) {
        return jdbc.sql("""
                SELECT catalog.article_id
                FROM public.courseware_catalog AS catalog
                WHERE %s
                ORDER BY catalog.article_id
                """.formatted(visibleArticlePredicate("catalog.article_id")))
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list();
    }

    @Override
    public String visibleArticlePredicate(String articleIdExpression) {
        return VISIBLE_PREDICATE.formatted(articleIdExpression);
    }
}
