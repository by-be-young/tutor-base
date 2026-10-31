package com.tutorbase.courseware.web;

import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.share.CoursewareCopyService;
import com.tutorbase.courseware.share.CoursewareShareService;
import com.tutorbase.courseware.notification.CoursewareNotificationService;
import com.tutorbase.courseware.update.CoursewareUpdateService;
import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final CoursewareUpdateService updates;
    private final CoursewareNotificationService notifications;

    CoursewareShareController(
            CoursewareShareService shares, CoursewareCopyService copies, CoursewareUpdateService updates,
            CoursewareNotificationService notifications) {
        this.shares = shares;
        this.copies = copies;
        this.updates = updates;
        this.notifications = notifications;
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
                        request.subject(), request.articleIds(), request.targetRootPath(),
                        request.conflictStrategy() == null ? "fail" : request.conflictStrategy())
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

    @GetMapping("/updates")
    ResponseEntity<DigestResponse> updates(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DigestResponse.from(updates.digest(teacherId(principal))));
    }

    @GetMapping("/copies/{copyId}/updates")
    ResponseEntity<UpdateDetailResponse> updateDetail(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long copyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(UpdateDetailResponse.from(updates.detail(teacherId(principal), copyId)));
    }

    @PostMapping("/copies/{copyId}/updates/apply")
    ResponseEntity<ApplyResponse> applyUpdate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long copyId,
            @jakarta.validation.Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApplyResponse.from(updates.apply(
                        teacherId(principal), copyId, request.mode(), request.expectedSourceHash())));
    }

    @PostMapping("/copies/{copyId}/updates/dismiss")
    ResponseEntity<ApplyResponse> dismissUpdate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long copyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApplyResponse.from(updates.apply(teacherId(principal), copyId, "keep", null)));
    }

    @GetMapping("/resources/articles/{teacherId}/{articleId}/preview")
    ResponseEntity<String> preview(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long teacherId,
            @PathVariable long articleId) {
        CoursewareCopyService.Preview preview = copies.preview(teacherId(principal), teacherId, articleId);
        return ResponseEntity.ok()
                .contentType(new org.springframework.http.MediaType(
                        "text", "markdown", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(preview.markdown());
    }

    @PatchMapping("/copies/{copyId}")
    ResponseEntity<CopySummaryResponse> rename(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long copyId,
            @jakarta.validation.Valid @RequestBody RenameRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CopySummaryResponse.from(
                        copies.rename(teacherId(principal), copyId, request.title(), request.logicalPath())));
    }

    @GetMapping("/notifications")
    ResponseEntity<NotificationCollectionResponse> notifications(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        long teacherId = teacherId(principal);
        List<NotificationResponse> items = notifications.list(teacherId, unreadOnly).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NotificationCollectionResponse(items, notifications.unreadCount(teacherId)));
    }

    @PostMapping("/notifications/read")
    ResponseEntity<NotificationReadResponse> markNotificationsRead(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody(required = false) MarkReadRequest request) {
        long teacherId = teacherId(principal);
        List<Long> ids = request == null ? List.of() : request.ids();
        int updated = notifications.markRead(teacherId, ids);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NotificationReadResponse(updated, notifications.unreadCount(teacherId)));
    }

    private static long teacherId(AccountPrincipal principal) {        if (principal == null) {
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
            @Size(max = 500) String targetRootPath,
            String conflictStrategy) {
    }

    record RenameRequest(@Size(max = 200) String title, @Size(max = 500) String logicalPath) {
    }

    record NotificationResponse(
            long id,
            String kind,
            String title,
            String body,
            Long sourceTeacherId,
            Long copyId,
            Long articleId,
            java.time.Instant createdAt,
            boolean read) {

        static NotificationResponse from(CoursewareNotificationService.Notification notification) {
            return new NotificationResponse(notification.id(), notification.kind(), notification.title(),
                    notification.body(), notification.sourceTeacherId(), notification.copyId(),
                    notification.articleId(), notification.createdAt(), notification.read());
        }
    }

    record NotificationCollectionResponse(List<NotificationResponse> items, long unreadCount) {
    }

    record MarkReadRequest(List<Long> ids) {
    }

    record NotificationReadResponse(int updated, long unreadCount) {
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

    record DigestItemResponse(
            Long copyId,
            Long localArticleId,
            long sourceArticleId,
            String title,
            String logicalPath,
            String baseContentHash,
            String sourceContentHash) {

        static DigestItemResponse from(CoursewareUpdateService.DigestItem item) {
            return new DigestItemResponse(item.copyId(), item.localArticleId(), item.sourceArticleId(),
                    item.title(), item.logicalPath(), item.baseContentHash(), item.sourceContentHash());
        }
    }

    record DigestResponse(
            int updatedCount,
            int addedCount,
            int removedCount,
            List<DigestItemResponse> updated,
            List<DigestItemResponse> added,
            List<DigestItemResponse> removed) {

        static DigestResponse from(CoursewareUpdateService.Digest digest) {
            return new DigestResponse(digest.updatedCount(), digest.addedCount(), digest.removedCount(),
                    digest.updated().stream().map(DigestItemResponse::from).toList(),
                    digest.added().stream().map(DigestItemResponse::from).toList(),
                    digest.removed().stream().map(DigestItemResponse::from).toList());
        }
    }

    record UpdateDetailResponse(
            long copyId,
            int sourceVersion,
            String baseHash,
            String sourceHash,
            boolean localModified,
            boolean answerChanged,
            String diff,
            String sourceTitle,
            String sourceLogicalPath) {

        static UpdateDetailResponse from(CoursewareUpdateService.UpdateDetail detail) {
            return new UpdateDetailResponse(detail.copyId(), detail.sourceVersion(), detail.baseHash(),
                    detail.sourceHash(), detail.localModified(), detail.answerChanged(), detail.diff(),
                    detail.sourceTitle(), detail.sourceLogicalPath());
        }
    }

    record ApplyRequest(@NotBlank String mode, String expectedSourceHash) {
    }

    record ApplyResponse(boolean applied, String backupHash, boolean answerKeyUpdated) {

        static ApplyResponse from(CoursewareUpdateService.ApplyResult result) {
            return new ApplyResponse(result.applied(), result.backupHash(), result.answerKeyUpdated());
        }
    }
}
