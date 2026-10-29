package com.tutorbase.administration.courseware;

import java.time.Instant;
import java.util.List;

/**
 * 管理员课件目录与认领关系。
 *
 * <p>学科认领与单篇认领相互独立：认领学科会覆盖该学科当前及未来全部课件，释放学科后
 * 只有单篇认领仍然生效。写操作均为幂等，返回已存在认领的原始 {@code claimedAt}。
 */
public interface CoursewareClaimAdministration {

    int syncCatalog(List<CatalogEntry> entries);

    Claims claims(long administratorAccountId);

    SubjectClaim claimSubject(long administratorAccountId, String subject);

    void releaseSubject(long administratorAccountId, String subject);

    ArticleClaim claimArticle(long administratorAccountId, long articleId);

    void releaseArticle(long administratorAccountId, long articleId);

    Catalog catalog(long administratorAccountId, CatalogScope scope);

    enum CatalogScope {
        mine, unclaimed, all
    }

    record CatalogEntry(long articleId, String subject, String title) {
    }

    record Claims(List<String> subjectClaims, List<Long> articleClaims) {

        public Claims {
            subjectClaims = List.copyOf(subjectClaims);
            articleClaims = List.copyOf(articleClaims);
        }
    }

    record SubjectClaim(String subject, Instant claimedAt) {
    }

    record ArticleClaim(long articleId, Instant claimedAt) {
    }

    record ClaimBy(long accountId, String username) {
    }

    record SubjectSummary(String subject, long total, boolean mine, List<String> claimedByOthers) {

        public SubjectSummary {
            claimedByOthers = List.copyOf(claimedByOthers);
        }
    }

    record CatalogItem(
            long articleId,
            String subject,
            String title,
            boolean individuallyClaimed,
            boolean coveredBySubjectClaim,
            List<ClaimBy> claimedBy) {

        public CatalogItem {
            claimedBy = List.copyOf(claimedBy);
        }
    }

    record Catalog(List<SubjectSummary> subjects, List<CatalogItem> items) {

        public Catalog {
            subjects = List.copyOf(subjects);
            items = List.copyOf(items);
        }
    }
}
