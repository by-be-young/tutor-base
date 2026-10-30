package com.tutorbase.courseware.web;

import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.share.CoursewareCopyService;
import com.tutorbase.courseware.share.CoursewareShareService;
import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 课件分享与获取：资源目录、共享树、复制到我的命名空间。 */
@RestController
@RequestMapping("/api/v1/admin/courseware")
public class CoursewareShareController {

    private final CoursewareShareService shares;
    private final CoursewareCopyService copies;

    CoursewareShareController(CoursewareShareService shares, CoursewareCopyService copies) {
        this.shares = shares;
        this.copies = copies;
    }

    @PostMapping("/shares")
    ResponseEntity<List<ShareResponse>> share(
            @AuthenticationPrincipal AccountPrincipal principal,
            @jakarta.validation.Valid @RequestBody ShareRequest request) {
        long teacherId = teacherId(principal);
        List<ShareResponse> created = shares.share(teacherId, request.subject(), request.scope(),
                        request.articleIds(), request.allowPreview(), request.allowCopy())
                .stream()
                .map(ShareResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping("/shares")
    ResponseEntity<List<ShareResponse>> myShares(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeRevoked) {
        List<ShareResponse> items = shares.myShares(teacherId(principal), includeRevoked).stream()
                .map(ShareResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }

    @DeleteMapping("/shares/{shareId}")
    ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long shareId) {
        shares.revoke(teacherId(principal), shareId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/resources")
    ResponseEntity<List<SharedSubjectResponse>> resources(@AuthenticationPrincipal AccountPrincipal principal) {
        List<SharedSubjectResponse> items = shares.resources(teacherId(principal)).stream()
                .map(SharedSubjectResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }

    @GetMapping("/resources/tree")
    ResponseEntity<List<SharedArticleResponse>> tree(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam long teacherId,
            @RequestParam String subject) {
        List<SharedArticleResponse> items = shares.tree(teacherId(principal), teacherId, subject).stream()
                .map(SharedArticleResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }

    @PostMapping("/copies")
    ResponseEntity<List<CopyResponse>> acquire(
            @AuthenticationPrincipal AccountPrincipal principal,
            @jakarta.validation.Valid @RequestBody AcquireRequest request) {
        List<CopyResponse> items = copies.acquire(teacherId(principal), request.sourceTeacherId(),
                        request.subject(), request.articleIds(), request.targetRootPath())
                .stream()
                .map(CopyResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }

    @GetMapping("/copies")
    ResponseEntity<List<CopySummaryResponse>> myCopies(@AuthenticationPrincipal AccountPrincipal principal) {
        List<CopySummaryResponse> items = copies.myCopies(teacherId(principal)).stream()
                .map(CopySummaryResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }

    private static long teacherId(AccountPrincipal principal) {
        if (principal == null) {
            throw new CoursewareFailures.Forbidden("未登录");
        }
        return principal.accountId();
    }

    record ShareRequest(
            @NotBlank @Size(max = 60) String subject,
            @NotBlank String scope,
            List<Long> articleIds,
            boolean allowPreview,
            boolean allowCopy) {

        ShareRequest {
            if ("article".equals(scope) && (articleIds == null || articleIds.isEmpty())) {
                throw new CoursewareFailures.Validation(List.of("单篇分享必须提供 articleIds"));
            }
        }
    }

    record ShareResponse(
            long shareId,
            String scope,
            String subject,
            Long articleId,
            boolean allowPreview,
            boolean allowCopy,
            boolean revoked) {

        static ShareResponse from(CoursewareShareService.Share share) {
            return new ShareResponse(share.shareId(), share.scope(), share.subject(), share.articleId(),
                    share.allowPreview(), share.allowCopy(), share.revoked());
        }
    }

    record SharedSubjectResponse(
            long teacherId,
            String teacherName,
            String subject,
            boolean wholeSubject,
            int articleCount,
            boolean allowCopy) {

        static SharedSubjectResponse from(CoursewareShareService.SharedSubject subject) {
            return new SharedSubjectResponse(subject.teacherId(), subject.teacherName(), subject.subject(),
                    subject.wholeSubject(), subject.articleCount(), subject.allowCopy());
        }
    }

    record SharedArticleResponse(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            int questionCount,
            boolean allowCopy) {

        static SharedArticleResponse from(CoursewareShareService.SharedArticle article) {
            return new SharedArticleResponse(article.articleId(), article.legacyArticleId(), article.title(),
                    article.series(), article.logicalPath(), article.contentHash(), article.questionCount(),
                    article.allowCopy());
        }
    }

    record AcquireRequest(
            long sourceTeacherId,
            @NotBlank @Size(max = 60) String subject,
            List<Long> articleIds,
            @Size(max = 500) String targetRootPath) {
    }

    record CopyResponse(
            long copyId,
            long localArticleId,
            String title,
            long sourceVersion,
            boolean alreadyExists) {

        static CopyResponse from(CoursewareCopyService.AcquireResult result) {
            return new CopyResponse(result.copyId(), result.localArticleId(), result.title(),
                    result.sourceVersion(), result.alreadyExists());
        }
    }

    record CopySummaryResponse(
            long copyId,
            long localArticleId,
            String title,
            long sourceTeacherId,
            String sourceTeacherName,
            long sourceArticleId,
            String sourceLogicalPath,
            String localLogicalPath,
            String baseContentHash,
            String sourceContentHash,
            String status,
            boolean hasUpdate) {

        static CopySummaryResponse from(CoursewareCopyService.CopySummary summary) {
            return new CopySummaryResponse(summary.copyId(), summary.localArticleId(), summary.title(),
                    summary.sourceTeacherId(), summary.sourceTeacherName(), summary.sourceArticleId(),
                    summary.sourceLogicalPath(), summary.localLogicalPath(), summary.baseContentHash(),
                    summary.sourceContentHash(), summary.status(), summary.hasUpdate());
        }
    }
}
