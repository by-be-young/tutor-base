package com.tutorbase.administration.courseware;

import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminCoursewareClaimController {

    private static final int MAX_CATALOG_ITEMS = 5_000;

    private final CoursewareClaimAdministration administration;

    public AdminCoursewareClaimController(CoursewareClaimAdministration administration) {
        this.administration = administration;
    }

    @PostMapping("/courseware-catalog/sync")
    SyncCatalogResponse syncCatalog(@Valid @RequestBody SyncCatalogRequest request) {
        List<CoursewareClaimAdministration.CatalogEntry> entries = request.items().stream()
                .map(item -> new CoursewareClaimAdministration.CatalogEntry(
                        item.articleId(),
                        item.subject().strip(),
                        item.title() == null ? null : item.title().strip()))
                .toList();
        return new SyncCatalogResponse(administration.syncCatalog(entries));
    }

    @GetMapping("/courseware-claims")
    CoursewareClaimsResponse claims(@AuthenticationPrincipal AccountPrincipal administrator) {
        CoursewareClaimAdministration.Claims claims = administration.claims(administrator.accountId());
        return new CoursewareClaimsResponse(
                claims.subjectClaims(), claims.articleClaims(), claims.excludedArticleIds());
    }

    @PutMapping("/courseware-claims/subjects/{subject}")
    SubjectClaimResponse claimSubject(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @PathVariable @NotBlank @Size(max = 100) String subject) {
        CoursewareClaimAdministration.SubjectClaim claim =
                administration.claimSubject(administrator.accountId(), subject);
        return new SubjectClaimResponse(claim.subject(), claim.claimedAt());
    }

    @DeleteMapping("/courseware-claims/subjects/{subject}")
    ResponseEntity<Void> releaseSubject(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @PathVariable @NotBlank @Size(max = 100) String subject) {
        administration.releaseSubject(administrator.accountId(), subject);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/courseware-claims/articles/{articleId}")
    ArticleClaimResponse claimArticle(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @PathVariable @Positive long articleId) {
        CoursewareClaimAdministration.ArticleClaim claim =
                administration.claimArticle(administrator.accountId(), articleId);
        return new ArticleClaimResponse(claim.articleId(), claim.claimedAt());
    }

    @DeleteMapping("/courseware-claims/articles/{articleId}")
    ResponseEntity<Void> releaseArticle(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @PathVariable @Positive long articleId) {
        administration.releaseArticle(administrator.accountId(), articleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courseware-claims/catalog")
    CatalogResponse catalog(
            @AuthenticationPrincipal AccountPrincipal administrator,
            @RequestParam(required = false, defaultValue = "mine") String scope) {
        CoursewareClaimAdministration.Catalog catalog =
                administration.catalog(administrator.accountId(), parseScope(scope));
        return CatalogResponse.from(catalog);
    }

    private static CoursewareClaimAdministration.CatalogScope parseScope(String scope) {
        return switch (scope) {
            case "mine" -> CoursewareClaimAdministration.CatalogScope.mine;
            case "unclaimed" -> CoursewareClaimAdministration.CatalogScope.unclaimed;
            case "all" -> CoursewareClaimAdministration.CatalogScope.all;
            default -> throw new InvalidCoursewareRequest(
                    "scope 参数必须是 mine、unclaimed 或 all。");
        };
    }

    record SyncCatalogRequest(
            @NotNull @Size(max = MAX_CATALOG_ITEMS) List<@Valid @NotNull SyncCatalogItemRequest> items) {
    }

    record SyncCatalogItemRequest(
            @Positive long articleId,
            @NotBlank @Size(max = 100) String subject,
            @Size(max = 300) String title) {
    }

    record SyncCatalogResponse(int synced) {
    }

    record CoursewareClaimsResponse(
            List<String> subjectClaims, List<Long> articleClaims, List<Long> excludedArticleIds) {
    }

    record SubjectClaimResponse(String subject, java.time.Instant claimedAt) {
    }

    record ArticleClaimResponse(long articleId, java.time.Instant claimedAt) {
    }

    record ClaimByResponse(long accountId, String username) {

        static ClaimByResponse from(CoursewareClaimAdministration.ClaimBy claimBy) {
            return new ClaimByResponse(claimBy.accountId(), claimBy.username());
        }
    }

    record SubjectSummaryResponse(String subject, long total, boolean mine, List<String> claimedByOthers) {

        static SubjectSummaryResponse from(CoursewareClaimAdministration.SubjectSummary summary) {
            return new SubjectSummaryResponse(
                    summary.subject(), summary.total(), summary.mine(), summary.claimedByOthers());
        }
    }

    record CatalogItemResponse(
            long articleId,
            String subject,
            String title,
            boolean individuallyClaimed,
            boolean coveredBySubjectClaim,
            boolean excluded,
            List<ClaimByResponse> claimedBy) {

        static CatalogItemResponse from(CoursewareClaimAdministration.CatalogItem item) {
            return new CatalogItemResponse(
                    item.articleId(),
                    item.subject(),
                    item.title(),
                    item.individuallyClaimed(),
                    item.coveredBySubjectClaim(),
                    item.excluded(),
                    item.claimedBy().stream().map(ClaimByResponse::from).toList());
        }
    }

    record CatalogResponse(List<SubjectSummaryResponse> subjects, List<CatalogItemResponse> items) {

        static CatalogResponse from(CoursewareClaimAdministration.Catalog catalog) {
            return new CatalogResponse(
                    catalog.subjects().stream().map(SubjectSummaryResponse::from).toList(),
                    catalog.items().stream().map(CatalogItemResponse::from).toList());
        }
    }
}
