package com.tutorbase.courseware.web;

import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.ops.CoursewareMaintenanceService;
import com.tutorbase.identity.AccountPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 课件运维：用量、配额、孤儿 blob 回收。 */
@RestController
@RequestMapping("/api/v1/admin/courseware/maintenance")
public class CoursewareMaintenanceController {

    private final CoursewareMaintenanceService maintenance;

    CoursewareMaintenanceController(CoursewareMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /** 我的用量（超管可传 teacherId 查他人）。 */
    @GetMapping("/usage")
    ResponseEntity<UsageResponse> usage(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Long teacherId) {
        long target = resolveTeacherId(principal, teacherId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(UsageResponse.from(maintenance.usage(target)));
    }

    /** 全站用量（仅超管）。 */
    @GetMapping("/usage/all")
    ResponseEntity<List<TeacherUsageResponse>> allUsage(@AuthenticationPrincipal AccountPrincipal principal) {
        requireSuperAdmin(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(maintenance.allUsage().stream().map(TeacherUsageResponse::from).toList());
    }

    /** 回收孤儿 blob；{@code dryRun=true} 只报告不删除。 */
    @PostMapping("/gc")
    ResponseEntity<GcResponse> collectGarbage(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        long target = resolveTeacherId(principal, teacherId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GcResponse.from(maintenance.collectGarbage(target, dryRun)));
    }

    private long resolveTeacherId(AccountPrincipal principal, Long requested) {
        if (principal == null) {
            throw new CoursewareFailures.Forbidden("未登录");
        }
        if (requested == null || requested == principal.accountId()) {
            return principal.accountId();
        }
        if (!principal.superAdmin()) {
            throw new CoursewareFailures.Forbidden("只能查看自己的用量");
        }
        return requested;
    }

    private void requireSuperAdmin(AccountPrincipal principal) {
        if (principal == null || !principal.superAdmin()) {
            throw new CoursewareFailures.Forbidden("仅超级管理员可查看全站用量");
        }
    }

    record UsageResponse(
            long teacherId,
            int blobCount,
            long totalBytes,
            int referencedCount,
            int orphanCount,
            long orphanBytes,
            long quotaBytes,
            boolean overQuota) {

        static UsageResponse from(CoursewareMaintenanceService.Usage usage) {
            return new UsageResponse(usage.teacherId(), usage.blobCount(), usage.totalBytes(),
                    usage.referencedCount(), usage.orphanCount(), usage.orphanBytes(),
                    usage.quotaBytes(), usage.overQuota());
        }
    }

    record TeacherUsageResponse(
            long teacherId,
            int blobCount,
            long totalBytes,
            int orphanCount,
            long orphanBytes,
            boolean overQuota) {

        static TeacherUsageResponse from(CoursewareMaintenanceService.TeacherUsage usage) {
            return new TeacherUsageResponse(usage.teacherId(), usage.blobCount(), usage.totalBytes(),
                    usage.orphanCount(), usage.orphanBytes(), usage.overQuota());
        }
    }

    record GcResponse(boolean dryRun, int removedCount, long freedBytes, List<String> removedHashes) {

        static GcResponse from(CoursewareMaintenanceService.GcResult result) {
            return new GcResponse(result.dryRun(), result.removedCount(), result.freedBytes(),
                    result.removedHashes());
        }
    }
}
