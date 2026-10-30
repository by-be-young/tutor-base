package com.tutorbase.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 教师注册申请：公开提交，只有超级管理员可以查看和审核。 */
@RestController
@Validated
@RequestMapping("/api/v1")
class TeacherApplicationController {
    private final TeacherApplicationService applications;
    private final IdentityRateLimiter rateLimiter;

    TeacherApplicationController(TeacherApplicationService applications, IdentityRateLimiter rateLimiter) {
        this.applications = applications;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/teacher-applications")
    ResponseEntity<TeacherApplicationResponse> submit(
            @Valid @RequestBody SubmitTeacherApplicationRequest body,
            HttpServletRequest request) {
        rateLimiter.check("teacher-application", request.getRemoteAddr());
        TeacherApplicationService.Application application =
                applications.submit(body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(TeacherApplicationResponse.from(application));
    }

    @GetMapping("/admin/teacher-applications")
    ResponseEntity<TeacherApplicationCollectionResponse> list(
            @RequestParam(required = false, defaultValue = "pending") String status,
            @AuthenticationPrincipal AccountPrincipal principal) {
        requireSuperAdministrator(principal);
        List<TeacherApplicationResponse> items = applications.list(parseStatus(status)).stream()
                .map(TeacherApplicationResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TeacherApplicationCollectionResponse(items, applications.pendingCount()));
    }

    @PostMapping("/admin/teacher-applications/{applicationId}/approve")
    ResponseEntity<ApprovedTeacherAccountResponse> approve(
            @PathVariable @Positive long applicationId,
            @AuthenticationPrincipal AccountPrincipal principal) {
        requireSuperAdministrator(principal);
        TeacherApplicationService.ApprovedAccount approved =
                applications.approve(applicationId, principal.accountId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApprovedTeacherAccountResponse.from(approved));
    }

    @PostMapping("/admin/teacher-applications/{applicationId}/reject")
    ResponseEntity<TeacherApplicationResponse> reject(
            @PathVariable @Positive long applicationId,
            @Valid @RequestBody RejectTeacherApplicationRequest body,
            @AuthenticationPrincipal AccountPrincipal principal) {
        requireSuperAdministrator(principal);
        TeacherApplicationService.Application rejected =
                applications.reject(applicationId, principal.accountId(), body.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TeacherApplicationResponse.from(rejected));
    }

    private static String parseStatus(String value) {
        String normalized = value == null ? "pending" : value.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pending", "approved", "rejected" -> normalized;
            case "all" -> null;
            default -> throw new TeacherApplicationService.InvalidApplicationStatus();
        };
    }

    private static void requireSuperAdministrator(AccountPrincipal principal) {
        if (principal == null || !principal.superAdmin()) {
            throw new TeacherApplicationService.SuperAdministratorRequired();
        }
    }

    record SubmitTeacherApplicationRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 6, max = 128) String password) {
    }

    record RejectTeacherApplicationRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record TeacherApplicationResponse(
            long id,
            String username,
            String status,
            Instant createdAt,
            Instant reviewedAt,
            String rejectionReason,
            Long approvedAccountId) {

        static TeacherApplicationResponse from(TeacherApplicationService.Application application) {
            return new TeacherApplicationResponse(
                    application.id(),
                    application.username(),
                    application.status().toUpperCase(Locale.ROOT),
                    application.createdAt(),
                    application.reviewedAt(),
                    application.rejectionReason(),
                    application.approvedAccountId());
        }
    }

    record TeacherApplicationCollectionResponse(List<TeacherApplicationResponse> items, long pendingCount) {
    }

    record ApprovedTeacherAccountResponse(
            long applicationId,
            long accountId,
            String username,
            String role,
            Instant approvedAt) {

        static ApprovedTeacherAccountResponse from(TeacherApplicationService.ApprovedAccount approved) {
            return new ApprovedTeacherAccountResponse(
                    approved.applicationId(),
                    approved.accountId(),
                    approved.username(),
                    approved.role().toUpperCase(Locale.ROOT),
                    approved.approvedAt());
        }
    }
}
