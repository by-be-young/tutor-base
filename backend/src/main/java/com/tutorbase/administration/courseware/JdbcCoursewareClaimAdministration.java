package com.tutorbase.administration.courseware;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcCoursewareClaimAdministration implements CoursewareClaimAdministration {

    private static final String DELETE_EXCLUSIONS_FOR_SUBJECT = """
            DELETE FROM public.admin_courseware_exclusion AS exclusion
            USING public.courseware_catalog AS catalog
            WHERE exclusion.admin_account_id = :administratorAccountId
              AND exclusion.article_id = catalog.article_id
              AND catalog.subject = :subject
            """;

    private static final String DELETE_ABANDONMENTS_FOR_SUBJECT = """
            DELETE FROM public.admin_courseware_abandonment AS abandonment
            USING public.courseware_catalog AS catalog
            WHERE abandonment.admin_account_id = :administratorAccountId
              AND abandonment.article_id = catalog.article_id
              AND catalog.subject = :subject
            """;

    private final JdbcClient jdbc;

    JdbcCoursewareClaimAdministration(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int syncCatalog(List<CatalogEntry> entries) {
        for (CatalogEntry entry : entries) {
            jdbc.sql("""
                    INSERT INTO public.courseware_catalog (article_id, subject, title, updated_at)
                    VALUES (:articleId, :subject, :title, now())
                    ON CONFLICT (article_id) DO UPDATE
                        SET subject = EXCLUDED.subject,
                            title = EXCLUDED.title,
                            updated_at = now()
                    """)
                    .param("articleId", entry.articleId())
                    .param("subject", entry.subject())
                    .param("title", entry.title())
                    .update();
        }
        refreshSpecialTestLearnerPermissions();
        return entries.size();
    }

    /**
     * 特殊测试学员拥有全部课件权限：目录同步后在同一事务内把 catalog 全部 article_id 写回其
     * permissions；catalog 为空时保留原值。article_id 为 bigint，permissions 为 integer[]。
     */
    private void refreshSpecialTestLearnerPermissions() {
        jdbc.sql("""
                UPDATE public.student AS special
                SET permissions = catalog_articles.article_ids
                FROM (
                    SELECT array_agg(catalog.article_id::integer ORDER BY catalog.article_id)
                               AS article_ids
                    FROM public.courseware_catalog AS catalog
                ) AS catalog_articles
                WHERE special.is_special_test
                  AND catalog_articles.article_ids IS NOT NULL
                """).update();
    }

    @Override
    @Transactional(readOnly = true)
    public Claims claims(long administratorAccountId) {
        List<String> subjectClaims = jdbc.sql("""
                SELECT subject
                FROM public.admin_subject_claim
                WHERE admin_account_id = :administratorAccountId
                ORDER BY subject
                """)
                .param("administratorAccountId", administratorAccountId)
                .query(String.class)
                .list();
        List<Long> articleClaims = jdbc.sql("""
                SELECT article_id
                FROM public.admin_courseware_claim
                WHERE admin_account_id = :administratorAccountId
                ORDER BY article_id
                """)
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list();
        List<Long> excludedArticleIds = jdbc.sql("""
                SELECT article_id
                FROM public.admin_courseware_exclusion
                WHERE admin_account_id = :administratorAccountId
                ORDER BY article_id
                """)
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list();
        List<Long> abandonedArticleIds = jdbc.sql("""
                SELECT article_id
                FROM public.admin_courseware_abandonment
                WHERE admin_account_id = :administratorAccountId
                ORDER BY article_id
                """)
                .param("administratorAccountId", administratorAccountId)
                .query(Long.class)
                .list();
        return new Claims(subjectClaims, articleClaims, excludedArticleIds, abandonedArticleIds);
    }

    @Override
    @Transactional
    public SubjectClaim claimSubject(long administratorAccountId, String subject) {
        String normalizedSubject = subject.strip();
        if (!subjectExists(normalizedSubject)) {
            throw new CoursewareNotFound("未找到学科：" + normalizedSubject);
        }
        jdbc.sql("""
                INSERT INTO public.admin_subject_claim (admin_account_id, subject)
                VALUES (:administratorAccountId, :subject)
                ON CONFLICT (admin_account_id, subject) DO NOTHING
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .update();
        // 重新认领整个学科等于恢复该学科全部课件，清除历史单篇排除与放弃记录。
        jdbc.sql(DELETE_EXCLUSIONS_FOR_SUBJECT)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .update();
        jdbc.sql(DELETE_ABANDONMENTS_FOR_SUBJECT)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .update();
        Instant claimedAt = jdbc.sql("""
                SELECT claimed_at
                FROM public.admin_subject_claim
                WHERE admin_account_id = :administratorAccountId
                  AND subject = :subject
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .query(JdbcCoursewareClaimAdministration::readClaimedAt)
                .single();
        return new SubjectClaim(normalizedSubject, claimedAt);
    }

    @Override
    @Transactional
    public void releaseSubject(long administratorAccountId, String subject) {
        String normalizedSubject = subject.strip();
        jdbc.sql("""
                DELETE FROM public.admin_subject_claim
                WHERE admin_account_id = :administratorAccountId
                  AND subject = :subject
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .update();
        // 释放学科后该学科的排除记录不再有意义，一并清理。
        jdbc.sql(DELETE_EXCLUSIONS_FOR_SUBJECT)
                .param("administratorAccountId", administratorAccountId)
                .param("subject", normalizedSubject)
                .update();
    }

    @Override
    @Transactional
    public ArticleClaim claimArticle(long administratorAccountId, long articleId) {
        if (!articleExists(articleId)) {
            throw new CoursewareNotFound("未找到课件（ID：" + articleId + "）。");
        }
        // 再次单独认领会清除排除与放弃记录；学科已覆盖时无需物化单篇认领。
        deleteExclusion(administratorAccountId, articleId);
        deleteAbandonment(administratorAccountId, articleId);
        jdbc.sql("""
                INSERT INTO public.admin_courseware_claim (admin_account_id, article_id)
                SELECT :administratorAccountId, :articleId
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM public.courseware_catalog AS catalog
                    JOIN public.admin_subject_claim AS subject_claim
                      ON subject_claim.subject = catalog.subject
                     AND subject_claim.admin_account_id = :administratorAccountId
                    WHERE catalog.article_id = :articleId
                )
                ON CONFLICT (admin_account_id, article_id) DO NOTHING
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
        Instant claimedAt = jdbc.sql("""
                SELECT COALESCE(courseware_claim.claimed_at, subject_claim.claimed_at) AS claimed_at
                FROM public.courseware_catalog AS catalog
                LEFT JOIN public.admin_courseware_claim AS courseware_claim
                  ON courseware_claim.article_id = catalog.article_id
                 AND courseware_claim.admin_account_id = :administratorAccountId
                LEFT JOIN public.admin_subject_claim AS subject_claim
                  ON subject_claim.subject = catalog.subject
                 AND subject_claim.admin_account_id = :administratorAccountId
                WHERE catalog.article_id = :articleId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .query(JdbcCoursewareClaimAdministration::readClaimedAt)
                .single();
        return new ArticleClaim(articleId, claimedAt);
    }

    @Override
    @Transactional
    public void releaseArticle(long administratorAccountId, long articleId) {
        // 先移除单篇认领；若该篇仍被学科覆盖，则写入排除行使其从“我的课件”消失。
        jdbc.sql("""
                DELETE FROM public.admin_courseware_claim
                WHERE admin_account_id = :administratorAccountId
                  AND article_id = :articleId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
        jdbc.sql("""
                INSERT INTO public.admin_courseware_exclusion (admin_account_id, article_id)
                SELECT :administratorAccountId, :articleId
                WHERE EXISTS (
                    SELECT 1
                    FROM public.courseware_catalog AS catalog
                    JOIN public.admin_subject_claim AS subject_claim
                      ON subject_claim.subject = catalog.subject
                     AND subject_claim.admin_account_id = :administratorAccountId
                    WHERE catalog.article_id = :articleId
                )
                ON CONFLICT (admin_account_id, article_id) DO NOTHING
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
    }

    @Override
    @Transactional
    public void abandonArticle(long administratorAccountId, long articleId) {
        if (!articleExists(articleId)) {
            throw new CoursewareNotFound("未找到课件（ID：" + articleId + "）。");
        }
        // 放弃先从“我的课件”移除认领；若学科仍覆盖该篇，写排除行使其不再可见。
        jdbc.sql("""
                DELETE FROM public.admin_courseware_claim
                WHERE admin_account_id = :administratorAccountId
                  AND article_id = :articleId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
        jdbc.sql("""
                INSERT INTO public.admin_courseware_exclusion (admin_account_id, article_id)
                SELECT :administratorAccountId, :articleId
                WHERE EXISTS (
                    SELECT 1
                    FROM public.courseware_catalog AS catalog
                    JOIN public.admin_subject_claim AS subject_claim
                      ON subject_claim.subject = catalog.subject
                     AND subject_claim.admin_account_id = :administratorAccountId
                    WHERE catalog.article_id = :articleId
                )
                ON CONFLICT (admin_account_id, article_id) DO NOTHING
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
        // 放弃记录使该课件进入“已放弃”，且不再计入该管理员的“待认领”。
        jdbc.sql("""
                INSERT INTO public.admin_courseware_abandonment (admin_account_id, article_id)
                VALUES (:administratorAccountId, :articleId)
                ON CONFLICT (admin_account_id, article_id) DO NOTHING
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Catalog catalog(long administratorAccountId, CatalogScope scope) {
        List<CatalogRow> rows = jdbc.sql("""
                SELECT article_id, subject, title
                FROM public.courseware_catalog
                ORDER BY subject, article_id
                """)
                .query(JdbcCoursewareClaimAdministration::mapCatalogRow)
                .list();
        List<SubjectClaimer> subjectClaimers = jdbc.sql("""
                SELECT claim.subject, claim.admin_account_id, account.username
                FROM public.admin_subject_claim AS claim
                JOIN public.account AS account ON account.id = claim.admin_account_id
                ORDER BY claim.subject, claim.admin_account_id
                """)
                .query((resultSet, rowNumber) -> new SubjectClaimer(
                        resultSet.getString("subject"),
                        resultSet.getLong("admin_account_id"),
                        resultSet.getString("username")))
                .list();
        List<ArticleClaimer> articleClaimers = jdbc.sql("""
                SELECT claim.article_id, claim.admin_account_id, account.username
                FROM public.admin_courseware_claim AS claim
                JOIN public.account AS account ON account.id = claim.admin_account_id
                ORDER BY claim.article_id, claim.admin_account_id
                """)
                .query((resultSet, rowNumber) -> new ArticleClaimer(
                        resultSet.getLong("article_id"),
                        resultSet.getLong("admin_account_id"),
                        resultSet.getString("username")))
                .list();
        List<Exclusion> exclusions = jdbc.sql("""
                SELECT admin_account_id, article_id
                FROM public.admin_courseware_exclusion
                ORDER BY article_id, admin_account_id
                """)
                .query((resultSet, rowNumber) -> new Exclusion(
                        resultSet.getLong("admin_account_id"),
                        resultSet.getLong("article_id")))
                .list();

        Claims myClaims = claims(administratorAccountId);
        Set<String> mySubjects = Set.copyOf(myClaims.subjectClaims());
        Set<Long> myArticles = Set.copyOf(myClaims.articleClaims());
        Set<Long> myExclusions = Set.copyOf(myClaims.excludedArticleIds());
        Set<Long> myAbandonments = Set.copyOf(myClaims.abandonedArticleIds());

        Map<String, List<ClaimBy>> subjectClaimersBySubject = new LinkedHashMap<>();
        for (SubjectClaimer claimer : subjectClaimers) {
            subjectClaimersBySubject
                    .computeIfAbsent(claimer.subject(), subject -> new ArrayList<>())
                    .add(new ClaimBy(claimer.accountId(), claimer.username()));
        }
        Map<Long, List<ClaimBy>> articleClaimersByArticle = new LinkedHashMap<>();
        for (ArticleClaimer claimer : articleClaimers) {
            articleClaimersByArticle
                    .computeIfAbsent(claimer.articleId(), articleId -> new ArrayList<>())
                    .add(new ClaimBy(claimer.accountId(), claimer.username()));
        }
        Map<Long, Set<Long>> excludedAccountIdsByArticle = new LinkedHashMap<>();
        for (Exclusion exclusion : exclusions) {
            excludedAccountIdsByArticle
                    .computeIfAbsent(exclusion.articleId(), articleId -> new HashSet<>())
                    .add(exclusion.accountId());
        }

        Map<String, Long> articleCountsBySubject = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            articleCountsBySubject.merge(row.subject(), 1L, Long::sum);
        }
        List<SubjectSummary> subjects = articleCountsBySubject.entrySet().stream()
                .map(entry -> new SubjectSummary(
                        entry.getKey(),
                        entry.getValue(),
                        mySubjects.contains(entry.getKey()),
                        subjectClaimersBySubject.getOrDefault(entry.getKey(), List.of()).stream()
                                .filter(claimer -> claimer.accountId() != administratorAccountId)
                                .map(ClaimBy::username)
                                .toList()))
                .toList();

        List<CatalogItem> items = new ArrayList<>();
        for (CatalogRow row : rows) {
            Set<Long> excludedAccountIds =
                    excludedAccountIdsByArticle.getOrDefault(row.articleId(), Set.of());
            TreeMap<Long, ClaimBy> claimers = new TreeMap<>();
            subjectClaimersBySubject.getOrDefault(row.subject(), List.of()).stream()
                    .filter(claimer -> !excludedAccountIds.contains(claimer.accountId()))
                    .forEach(claimer -> claimers.putIfAbsent(claimer.accountId(), claimer));
            articleClaimersByArticle.getOrDefault(row.articleId(), List.of())
                    .forEach(claimer -> claimers.putIfAbsent(claimer.accountId(), claimer));
            boolean excluded = myExclusions.contains(row.articleId());
            boolean abandoned = myAbandonments.contains(row.articleId());
            boolean coveredBySubjectClaim = mySubjects.contains(row.subject()) && !excluded;
            boolean individuallyClaimed = myArticles.contains(row.articleId());
            boolean visible = (coveredBySubjectClaim || individuallyClaimed) && !abandoned;
            boolean included = switch (scope) {
                case all -> true;
                case mine -> visible;
                case unclaimed -> claimers.isEmpty() && !abandoned;
                case abandoned -> abandoned;
            };
            if (included) {
                items.add(new CatalogItem(
                        row.articleId(),
                        row.subject(),
                        row.title(),
                        individuallyClaimed,
                        coveredBySubjectClaim,
                        excluded,
                        abandoned,
                        List.copyOf(claimers.values())));
            }
        }
        return new Catalog(subjects, items);
    }

    private boolean subjectExists(String subject) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public.courseware_catalog WHERE subject = :subject
                )
                """)
                .param("subject", subject)
                .query(Boolean.class)
                .single();
    }

    private boolean articleExists(long articleId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public.courseware_catalog WHERE article_id = :articleId
                )
                """)
                .param("articleId", articleId)
                .query(Boolean.class)
                .single();
    }

    private void deleteExclusion(long administratorAccountId, long articleId) {
        jdbc.sql("""
                DELETE FROM public.admin_courseware_exclusion
                WHERE admin_account_id = :administratorAccountId
                  AND article_id = :articleId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
    }

    private void deleteAbandonment(long administratorAccountId, long articleId) {
        jdbc.sql("""
                DELETE FROM public.admin_courseware_abandonment
                WHERE admin_account_id = :administratorAccountId
                  AND article_id = :articleId
                """)
                .param("administratorAccountId", administratorAccountId)
                .param("articleId", articleId)
                .update();
    }

    private static CatalogRow mapCatalogRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CatalogRow(
                resultSet.getLong("article_id"),
                resultSet.getString("subject"),
                resultSet.getString("title"));
    }

    private static Instant readClaimedAt(ResultSet resultSet, int rowNumber) throws SQLException {
        return resultSet.getTimestamp("claimed_at").toInstant();
    }

    private record CatalogRow(long articleId, String subject, String title) {
    }

    private record SubjectClaimer(String subject, long accountId, String username) {
    }

    private record ArticleClaimer(long articleId, long accountId, String username) {
    }

    private record Exclusion(long accountId, long articleId) {
    }
}
