package com.tutorbase.courseware.query;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「我的课件」概览：替代旧「认领」页的入口数据。
 *
 * <p>给出每个科目的当前发布版本、篇数、草稿数、被获取数与待处理更新数，
 * 让老师一眼看出哪里需要发布、哪里有更新。
 */
@Service
public class CoursewareOverviewService {

    private final JdbcClient jdbc;
    private final com.tutorbase.courseware.storage.CoursewareStorage storage;
    private final com.tutorbase.courseware.CoursewareProperties properties;

    CoursewareOverviewService(JdbcClient jdbc,
            com.tutorbase.courseware.storage.CoursewareStorage storage,
            com.tutorbase.courseware.CoursewareProperties properties) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Overview overview(long teacherId) {
        List<SubjectOverview> subjects = jdbc.sql("""
                SELECT h.subject, r.version, r.published_at,
                       (SELECT count(*) FROM public.course_article AS ca WHERE ca.release_id = h.release_id)
                           AS article_count,
                       (SELECT count(*) FROM public.course_draft AS d
                         WHERE d.teacher_account_id = h.teacher_account_id
                           AND EXISTS (
                               SELECT 1 FROM public.course_article AS ca2
                               WHERE ca2.release_id = h.release_id
                                 AND ca2.article_id = d.article_id
                           )) AS draft_count,
                       (SELECT count(*) FROM public.course_copy AS c
                         WHERE c.source_teacher_account_id = h.teacher_account_id
                           AND EXISTS (
                               SELECT 1 FROM public.course_article AS ca3
                               WHERE ca3.release_id = h.release_id
                                 AND ca3.article_id = c.source_article_id
                           )) AS copied_count,
                       (SELECT count(*) FROM public.course_share AS s
                         WHERE s.owner_teacher_account_id = h.teacher_account_id
                           AND s.subject = h.subject AND s.revoked_at IS NULL) AS share_count
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                WHERE h.teacher_account_id = :teacherId
                ORDER BY h.subject
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new SubjectOverview(
                        rs.getString("subject"),
                        rs.getInt("version"),
                        rs.getObject("published_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getInt("article_count"),
                        rs.getInt("draft_count"),
                        rs.getInt("copied_count"),
                        rs.getInt("share_count")))
                .list();

        int pendingDrafts = jdbc.sql("""
                SELECT count(*) FROM public.course_draft WHERE teacher_account_id = :teacherId
                """)
                .param("teacherId", teacherId)
                .query(Integer.class)
                .single();
        int acquired = jdbc.sql("""
                SELECT count(*) FROM public.course_copy
                WHERE owner_account_id = :teacherId AND status = 'active'
                """)
                .param("teacherId", teacherId)
                .query(Integer.class)
                .single();
        int pendingUpdates = jdbc.sql("""
                SELECT count(*) FROM public.course_copy AS c
                JOIN public.course_article AS ca
                  ON ca.teacher_account_id = c.source_teacher_account_id
                 AND ca.article_id = c.source_article_id
                JOIN public.course_release_head AS h
                  ON h.teacher_account_id = c.source_teacher_account_id
                 AND h.release_id = ca.release_id
                WHERE c.owner_account_id = :teacherId
                  AND c.status = 'active'
                  AND replace(ca.content_hash, 'sha256:', '') <> replace(c.base_content_hash, 'sha256:', '')
                  AND (c.dismissed_hash IS NULL
                       OR replace(ca.content_hash, 'sha256:', '') <> replace(c.dismissed_hash, 'sha256:', ''))
                """)
                .param("teacherId", teacherId)
                .query(Integer.class)
                .single();
        int unreadNotifications = jdbc.sql("""
                SELECT count(*) FROM public.course_notification
                WHERE recipient_account_id = :teacherId AND read_at IS NULL
                """)
                .param("teacherId", teacherId)
                .query(Integer.class)
                .single();

        return new Overview(subjects, pendingDrafts, acquired, pendingUpdates, unreadNotifications);
    }

    /** 一个科目的当前状态。 */
    public record SubjectOverview(
            String subject,
            int version,
            Instant publishedAt,
            int articleCount,
            int draftCount,
            int copiedCount,
            int shareCount) {
    }

    /**
     * 「我的课件」清单：已发布篇目（当前生效版本）+ 尚未发布的草稿。
     *
     * <p>在线编辑器与「课件」页的科目目录都以它为数据源；只看 course_draft 会因为
     * 通过 CLI / 导入发布、没有草稿行的课件完全选不到。
     */
    @Transactional(readOnly = true)
    public List<ArticleSummary> articles(long teacherId) {
        // 已发布篇目的正文是否存在：卷重建/历史环境临时目录被清空后会丢 blob，
        // 清单必须如实标出来，否则点开只会得到一句"课件不存在"。
        // 静态模式下正文不在服务端托管，不做这个判断（避免整片误报）。
        java.util.Set<String> storedHashes = "storage".equals(properties.source())
                ? storage.listBlobs(teacherId).keySet().stream()
                        .map(com.tutorbase.courseware.storage.CoursewareHashes::normalize)
                        .collect(java.util.stream.Collectors.toSet())
                : null;
        List<ArticleSummary> published = jdbc.sql("""
                SELECT h.subject, ca.article_id, ca.legacy_article_id, ca.title, ca.series,
                       ca.logical_path, ca.content_hash,
                       d.markdown IS NOT NULL AS has_draft,
                       COALESCE(length(d.markdown), 0) AS size,
                       d.updated_at AS draft_updated_at
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = h.teacher_account_id
                LEFT JOIN public.course_draft AS d
                  ON d.teacher_account_id = h.teacher_account_id
                 AND d.article_id = ca.article_id
                WHERE h.teacher_account_id = :teacherId
                ORDER BY h.subject, ca.logical_path
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new ArticleSummary(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("subject"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        true,
                        rs.getBoolean("has_draft"),
                        rs.getInt("size"),
                        toInstant(rs, "draft_updated_at"),
                        true,
                        storedHashes != null && !storedHashes.contains(
                                com.tutorbase.courseware.storage.CoursewareHashes.normalize(
                                        rs.getString("content_hash")))))
                .list();

        // 静态课件不再在这里列出来：正文必须已经由服务端导入成发布版本（或草稿），
        // 浏览器不直接读取 public/articles，避免「看得见却打不开」的条目。
        List<ArticleSummary> drafts = jdbc.sql("""
                SELECT d.article_id, d.markdown, d.updated_at
                FROM public.course_draft AS d
                WHERE d.teacher_account_id = :teacherId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.course_release_head AS h
                      JOIN public.course_article AS ca
                        ON ca.release_id = h.release_id
                       AND ca.teacher_account_id = h.teacher_account_id
                      WHERE h.teacher_account_id = d.teacher_account_id
                        AND ca.article_id = d.article_id
                  )
                ORDER BY d.updated_at DESC
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new ArticleSummary(
                        rs.getLong("article_id"),
                        null,
                        firstHeading(rs.getString("markdown")),
                        null,
                        null,
                        null,
                        false,
                        true,
                        rs.getString("markdown").length(),
                        toInstant(rs, "updated_at"),
                        true,
                        false))
                .list();

        List<ArticleSummary> merged = new java.util.ArrayList<>(published);
        merged.addAll(drafts);
        return merged;
    }

    private static Instant toInstant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        java.time.OffsetDateTime value = resultSet.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** 草稿没有 frontmatter 元数据时，用正文第一个一级标题当标题。 */
    private static String firstHeading(String markdown) {        if (markdown == null) {
            return "";
        }
        for (String line : markdown.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return "";
    }

    /** 编辑器选择器与「课件」页的一行。 */
    public record ArticleSummary(
            long articleId,
            Long legacyArticleId,
            String title,
            String subject,
            String series,
            String logicalPath,
            boolean published,
            boolean hasDraft,
            int size,
            Instant updatedAt,
            /** 正文是否已由数据库托管；当前接口只返回托管篇目，保留字段兼容前端。 */
            boolean hosted,
            /** true = 已发布篇目的正文文件（blob）不在了：编辑器会回退到静态讲义并提示重新发布。 */
            boolean contentMissing) {
    }

    /** 概览。 */
    public record Overview(
            List<SubjectOverview> subjects,
            int pendingDrafts,
            int acquiredCount,
            int pendingUpdates,
            int unreadNotifications) {
    }
}
