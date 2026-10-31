package com.tutorbase.courseware.ops;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运维：配额、用量统计、孤儿 blob 回收。
 *
 * <p>正文内容寻址，同一份内容只存一份，所以配额按"该老师命名空间下 blob 的实际字节数"算。
 * GC 只删**任何地方都不再引用**的 blob：{@code course_article.content_hash}（含历史版本）、
 * {@code course_release.manifest_hash}、{@code course_copy.base_content_hash/dismissed_hash}、
 * 以及草稿正文的 hash 都算引用 —— 少算一项就会删掉学员历史记录要用到的正文。
 */
@Service
public class CoursewareMaintenanceService {

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewareProperties properties;

    CoursewareMaintenanceService(JdbcClient jdbc, CoursewareStorage storage, CoursewareProperties properties) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
    }

    /** 用量：blob 数、总字节、被引用数、孤儿数与配额。 */
    @Transactional(readOnly = true)
    public Usage usage(long teacherId) {
        Map<String, Long> blobs = storage.listBlobs(teacherId);
        long totalBytes = blobs.values().stream().mapToLong(Long::longValue).sum();
        Set<String> referenced = referencedHashes(teacherId);
        Set<String> orphans = new LinkedHashSet<>(blobs.keySet());
        orphans.removeAll(referenced);
        long orphanBytes = orphans.stream().mapToLong(hash -> blobs.getOrDefault(hash, 0L)).sum();
        long quota = properties.quotaBytes();
        return new Usage(teacherId, blobs.size(), totalBytes, referenced.size(), orphans.size(),
                orphanBytes, quota, quota > 0 && totalBytes > quota);
    }

    /** 发布/获取/导入前的配额检查；超限抛 413。 */
    @Transactional(readOnly = true)
    public void requireWithinQuota(long teacherId, long incomingBytes) {
        long quota = properties.quotaBytes();
        if (quota <= 0) {
            return;
        }
        Usage usage = usage(teacherId);
        if (usage.totalBytes() + Math.max(0, incomingBytes) > quota) {
            throw new CoursewareFailures.QuotaExceeded(
                    "课件存储已超出配额（" + usage.totalBytes() + " / " + quota + " 字节），请清理或扩容");
        }
    }

    /**
     * 回收孤儿 blob。
     *
     * @param dryRun true 时只列出将被删除的 blob，不实际删除
     */
    @Transactional
    public GcResult collectGarbage(long teacherId, boolean dryRun) {
        Map<String, Long> blobs = storage.listBlobs(teacherId);
        Set<String> referenced = referencedHashes(teacherId);
        List<String> removed = new ArrayList<>();
        long freed = 0;
        for (Map.Entry<String, Long> entry : blobs.entrySet()) {
            if (referenced.contains(entry.getKey())) {
                continue;
            }
            if (!dryRun && !storage.deleteBlob(teacherId, entry.getKey())) {
                continue;
            }
            removed.add(entry.getKey());
            freed += entry.getValue();
        }
        return new GcResult(dryRun, removed.size(), freed, removed);
    }

    /** 全站统计：每个出现过课件的老师的用量。 */
    @Transactional(readOnly = true)
    public List<TeacherUsage> allUsage() {
        List<Long> teacherIds = jdbc.sql("""
                SELECT DISTINCT teacher_account_id AS id FROM public.course_article
                UNION
                SELECT DISTINCT teacher_account_id FROM public.course_draft
                UNION
                SELECT DISTINCT owner_account_id FROM public.course_copy
                ORDER BY 1
                """)
                .query(Long.class)
                .list();
        List<TeacherUsage> rows = new ArrayList<>();
        for (Long teacherId : teacherIds) {
            Usage usage = usage(teacherId);
            rows.add(new TeacherUsage(teacherId, usage.blobCount(), usage.totalBytes(),
                    usage.orphanCount(), usage.orphanBytes(), usage.overQuota()));
        }
        return rows;
    }

    private Set<String> referencedHashes(long teacherId) {
        Set<String> referenced = new LinkedHashSet<>();
        queryStrings("""
                SELECT DISTINCT content_hash FROM public.course_article
                WHERE teacher_account_id = :teacherId
                """, teacherId).forEach(referenced::add);
        queryStrings("""
                SELECT manifest_hash FROM public.course_release WHERE teacher_account_id = :teacherId
                """, teacherId).forEach(referenced::add);
        jdbc.sql("""
                SELECT base_content_hash, dismissed_hash FROM public.course_copy
                WHERE owner_account_id = :teacherId OR source_teacher_account_id = :teacherId
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> java.util.Arrays.asList(
                        rs.getString("base_content_hash"), rs.getString("dismissed_hash")))
                .list()
                .forEach(pair -> pair.stream().filter(Objects::nonNull).forEach(referenced::add));
        queryStrings("""
                SELECT markdown FROM public.course_draft WHERE teacher_account_id = :teacherId
                """, teacherId)
                .forEach(markdown -> referenced.add(
                        LocalFileStorage.sha256(markdown.getBytes(StandardCharsets.UTF_8))));
        return referenced;
    }

    private List<String> queryStrings(String sql, long teacherId) {
        return jdbc.sql(sql)
                .param("teacherId", teacherId)
                .query(String.class)
                .list();
    }

    /** 一个老师的用量。 */
    public record Usage(
            long teacherId,
            int blobCount,
            long totalBytes,
            int referencedCount,
            int orphanCount,
            long orphanBytes,
            long quotaBytes,
            boolean overQuota) {
    }

    /** GC 结果。 */
    public record GcResult(boolean dryRun, int removedCount, long freedBytes, List<String> removedHashes) {
    }

    /** 全站里一个老师的用量。 */
    public record TeacherUsage(
            long teacherId,
            int blobCount,
            long totalBytes,
            int orphanCount,
            long orphanBytes,
            boolean overQuota) {
    }
}
