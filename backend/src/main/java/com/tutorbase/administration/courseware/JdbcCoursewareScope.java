package com.tutorbase.administration.courseware;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class JdbcCoursewareScope implements CoursewareScope {

    /**
     * 归属判定：管理员发布了该 legacy 篇目（当前生效版本内），即可见。
     *
     * <p>不依赖 {@code courseware_catalog} 与旧认领表，因此 V26 换主键后仍然成立；
     * 必须 join {@code course_release_head} 限定当前生效版本，否则被历史版本行干扰。
     */
    private static final String OWNED_PREDICATE = """
            EXISTS (
                SELECT 1
                FROM public.course_release_head AS owned_head
                JOIN public.course_article AS owned_article
                  ON owned_article.teacher_account_id = owned_head.teacher_account_id
                 AND owned_article.release_id = owned_head.release_id
                WHERE owned_head.teacher_account_id = :administratorAccountId
                  AND owned_article.legacy_article_id = %s
            )""";

    /** 旧认领模型判定：科目认领 / 单篇认领，可被排除或放弃。认领下线后逐步失效。 */
    private static final String LEGACY_VISIBLE_PREDICATE = """
            EXISTS (
                SELECT 1
                FROM public.courseware_catalog AS scoped_catalog
                WHERE scoped_catalog.article_id = %s
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.admin_courseware_abandonment AS scoped_abandonment
                      WHERE scoped_abandonment.admin_account_id = :administratorAccountId
                        AND scoped_abandonment.article_id = scoped_catalog.article_id
                  )
                  AND (
                      EXISTS (
                          SELECT 1
                          FROM public.admin_subject_claim AS scoped_subject_claim
                          WHERE scoped_subject_claim.admin_account_id = :administratorAccountId
                            AND scoped_subject_claim.subject = scoped_catalog.subject
                            AND NOT EXISTS (
                                SELECT 1
                                FROM public.admin_courseware_exclusion AS scoped_exclusion
                                WHERE scoped_exclusion.admin_account_id = :administratorAccountId
                                  AND scoped_exclusion.article_id = scoped_catalog.article_id
                            )
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
        List<Long> owned = jdbc.sql("""
                SELECT DISTINCT owned_article.legacy_article_id
                FROM public.course_release_head AS owned_head
                JOIN public.course_article AS owned_article
                  ON owned_article.teacher_account_id = owned_head.teacher_account_id
                 AND owned_article.release_id = owned_head.release_id
                WHERE owned_head.teacher_account_id = :administratorAccountId
                  AND owned_article.legacy_article_id IS NOT NULL
                """)
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list();
        Stream<Long> legacy = jdbc.sql("""
                SELECT catalog.article_id
                FROM public.courseware_catalog AS catalog
                WHERE %s
                ORDER BY catalog.article_id
                """.formatted(LEGACY_VISIBLE_PREDICATE.formatted("catalog.article_id")))
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list()
                .stream();
        return Stream.concat(owned.stream(), legacy).distinct().sorted().toList();
    }

    @Override
    public String visibleArticlePredicate(String articleIdExpression) {
        return "(" + OWNED_PREDICATE.formatted(articleIdExpression)
                + " OR " + LEGACY_VISIBLE_PREDICATE.formatted(articleIdExpression) + ")";
    }
}

