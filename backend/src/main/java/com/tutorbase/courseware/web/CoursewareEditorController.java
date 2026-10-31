package com.tutorbase.courseware.web;

import java.time.Instant;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.draft.CoursewareDraftService;
import com.tutorbase.courseware.layout.CoursewareLayoutService;
import com.tutorbase.courseware.layout.CoursewareTemplateService;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.transfer.CoursewareTransferService;
import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 在线编辑器：布局注册表、草稿、从草稿发布、导出导入。 */
@RestController
@RequestMapping("/api/v1/admin/courseware")
public class CoursewareEditorController {

    private final CoursewareLayoutService layouts;
    private final CoursewareDraftService drafts;
    private final CoursewareTransferService transfers;
    private final CoursewareTemplateService templates;

    CoursewareEditorController(CoursewareLayoutService layouts, CoursewareDraftService drafts,
            CoursewareTransferService transfers, CoursewareTemplateService templates) {
        this.layouts = layouts;
        this.drafts = drafts;
        this.transfers = transfers;
        this.templates = templates;
    }

    @GetMapping("/templates")
    ResponseEntity<List<TemplateResponse>> templates(@RequestParam(required = false) String subject) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(templates.list(subject).stream().map(TemplateResponse::from).toList());
    }

    @GetMapping("/templates/{templateId}")
    ResponseEntity<SkeletonResponse> template(@PathVariable long templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new SkeletonResponse(templates.markdown(templateId)));
    }

    @PostMapping("/templates")
    ResponseEntity<TemplateResponse> saveTemplate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SaveTemplateRequest request) {
        teacherId(principal);
        long id = templates.save(request.subject(), request.kind(), request.name(), request.markdown());
        return templates.list(request.subject()).stream()
                .filter(template -> template.id() == id)
                .findFirst()
                .map(template -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(TemplateResponse.from(template)))
                .orElseThrow(() -> new CoursewareFailures.NotFound("模板保存后不可见：" + id));
    }

    @DeleteMapping("/templates/{templateId}")
    ResponseEntity<Void> deleteTemplate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long templateId) {
        teacherId(principal);
        templates.deactivate(templateId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/layouts")
    ResponseEntity<List<LayoutResponse>> layouts() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(layouts.active().stream().map(LayoutResponse::from).toList());
    }

    @GetMapping("/layouts/{layoutId}/skeleton")
    ResponseEntity<SkeletonResponse> skeleton(
            @PathVariable String layoutId,
            @RequestParam(defaultValue = "新课件") String title) {
        if (layouts.active().stream().noneMatch(layout -> layout.layoutId().equals(layoutId))) {
            throw new CoursewareFailures.NotFound("布局不存在或已停用：" + layoutId);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new SkeletonResponse(CoursewareLayoutService.skeleton(title, layoutId)));
    }

    @GetMapping("/drafts")
    ResponseEntity<List<DraftSummaryResponse>> drafts(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(drafts.list(teacherId(principal)).stream().map(DraftSummaryResponse::from).toList());
    }

    @GetMapping("/drafts/{articleId}")
    ResponseEntity<DraftResponse> draft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long articleId) {
        CoursewareDraftService.Draft draft = drafts.get(teacherId(principal), articleId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DraftResponse(draft.articleId(), draft.markdown(), draft.hasDraft()));
    }

    @PutMapping("/drafts/{articleId}")
    ResponseEntity<DraftResponse> saveDraft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long articleId,
            @Valid @RequestBody SaveDraftRequest request) {
        CoursewareDraftService.Draft draft =
                drafts.save(teacherId(principal), articleId, request.legacyArticleId(), request.markdown());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DraftResponse(draft.articleId(), draft.markdown(), draft.hasDraft()));
    }

    @PostMapping("/drafts")
    ResponseEntity<DraftResponse> createDraft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SaveDraftRequest request) {
        CoursewareDraftService.Draft draft =
                drafts.save(teacherId(principal), null, request.legacyArticleId(), request.markdown());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DraftResponse(draft.articleId(), draft.markdown(), draft.hasDraft()));
    }

    @DeleteMapping("/drafts/{articleId}")
    ResponseEntity<Void> deleteDraft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long articleId) {
        drafts.delete(teacherId(principal), articleId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/drafts/precheck")
    ResponseEntity<PrecheckResponse> precheck(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SaveDraftRequest request) {
        List<String> errors = drafts.precheck(teacherId(principal), request.markdown());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PrecheckResponse(errors.isEmpty(), errors));
    }

    @PostMapping("/drafts/publish")
    ResponseEntity<ReleaseResponse> publishDrafts(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PublishDraftsRequest request) {
        CoursewarePublisher.Published published =
                drafts.publish(teacherId(principal), request.subject(), request.expectVersion());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReleaseResponse.from(published));
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) List<Long> articleIds) {
        CoursewareTransferService.Export export =
                transfers.export(teacherId(principal), subject, articleIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .body(export.content());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportReportResponse> importZip(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean overwrite) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new CoursewareFailures.Validation(List.of("请上传 zip 文件"));
        }
        CoursewareTransferService.ImportReport report =
                transfers.importZip(teacherId(principal), file.getInputStream(), overwrite);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ImportReportResponse.from(report));
    }

    private static long teacherId(AccountPrincipal principal) {
        if (principal == null) {
            throw new CoursewareFailures.Forbidden("未登录");
        }
        return principal.accountId();
    }

    record LayoutResponse(
            String layoutId,
            String name,
            String component,
            List<String> allowedBlocks,
            int version) {

        static LayoutResponse from(CoursewareLayoutService.Layout layout) {
            return new LayoutResponse(layout.layoutId(), layout.name(), layout.component(),
                    layout.allowedBlocks(), layout.version());
        }
    }

    record SkeletonResponse(String markdown) {
    }

    record TemplateResponse(long id, String subject, String kind, String name) {

        static TemplateResponse from(CoursewareTemplateService.Template template) {
            return new TemplateResponse(template.id(), template.subject(), template.kind(), template.name());
        }
    }

    record SaveTemplateRequest(
            String subject,
            @NotBlank String kind,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1_048_576) String markdown) {
    }

    record SaveDraftRequest(
            Long legacyArticleId,
            @NotBlank @Size(max = 1_048_576) String markdown) {
    }

    record DraftResponse(long articleId, String markdown, boolean hasDraft) {
    }

    record DraftSummaryResponse(
            long articleId,
            String title,
            String subject,
            String series,
            String logicalPath,
            int size,
            Instant updatedAt,
            boolean published) {

        static DraftSummaryResponse from(CoursewareDraftService.DraftSummary summary) {
            return new DraftSummaryResponse(summary.articleId(), summary.title(), summary.subject(),
                    summary.series(), summary.logicalPath(), summary.size(), summary.updatedAt(),
                    summary.published());
        }
    }

    record PrecheckResponse(boolean ok, List<String> errors) {
    }

    record PublishDraftsRequest(
            @NotBlank @Size(max = 60) String subject,
            Integer expectVersion) {
    }

    record ReleaseResponse(
            long teacherId,
            String subject,
            int version,
            String contentHash,
            int articleCount,
            Instant publishedAt) {

        static ReleaseResponse from(CoursewarePublisher.Published published) {
            return new ReleaseResponse(published.teacherId(), published.subject(), published.version(),
                    published.contentHash(), published.articleCount(), published.publishedAt());
        }
    }

    record ImportItemResponse(String logicalPath, Long articleId, String status, String message) {

        static ImportItemResponse from(CoursewareTransferService.ImportItem item) {
            return new ImportItemResponse(item.logicalPath(), item.articleId(), item.status(), item.message());
        }
    }

    record ImportReportResponse(int imported, int overwritten, int skipped, List<ImportItemResponse> items) {

        static ImportReportResponse from(CoursewareTransferService.ImportReport report) {
            return new ImportReportResponse(report.imported(), report.overwritten(), report.skipped(),
                    report.items().stream().map(ImportItemResponse::from).toList());
        }
    }
}
