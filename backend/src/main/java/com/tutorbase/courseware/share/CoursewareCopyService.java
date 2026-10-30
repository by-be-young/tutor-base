package com.tutorbase.courseware.share;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.storage.CoursewareStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 获取课件（复制到自己的命名空间）。
 *
 * <p>内容寻址让复制零成本：同一份正文在物理上只存一份，换的是归属与 {@code course_copy} 的来源关系。
 * 复制结果先落在 {@code course_draft}（未发布），下次发布时随当前版本一起上线；
 * 答案键随正文同批（记录 {@code base_answer_hash}），供后续更新检测判断"答案是否也变了"。
 */
@Service
public class CoursewareCopyService {

    private static final long FIRST_LOCAL_ARTICLE_ID = 1000;

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewareShareService shares;

    CoursewareCopyService(JdbcClient jdbc, CoursewareStorage storage, CoursewareShareService shares) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.shares = shares;
    }

    /**
     * 获取整科（{@code articleIds} 为空）或指定单篇。
     *
     * @param targetRootPath 可选：把复制结果放到自己目录树的指定前缀下
     */
    @Transactional
    public List<AcquireResult> acquire(
            long teacherId, long sourceTeacherId, String subject, List<Long> articleIds, String targetRootPath) {
        Map<Long, Boolean> policy = shares.sharePolicy(sourceTeacherId, subject);
        if (policy.isEmpty()) {
            throw new CoursewareFailures.SourceForbidden("来源未分享该科目，无法获取：" + subject);
        }
        boolean wholeSubject = policy.containsKey(null);
        List<CoursewareShareService.PublishedArticle> published = shares.publishedArticles(sourceTeacherId, subject);
        if (published.isEmpty()) {
            throw new CoursewareFailures.NotFound("来源没有已发布内容：" + subject);
        }

        List<CoursewareShareService.PublishedArticle> targets = new ArrayList<>();
        if (articleIds == null || articleIds.isEmpty()) {
            if (!wholeSubject) {
                throw new CoursewareFailures.SourceForbidden("来源只分享了部分篇目，请指定 articleIds");
            }
            targets.addAll(published);
        } else {
            Map<Long, CoursewareShareService.PublishedArticle> byId = new LinkedHashMap<>();
            published.forEach(article -> byId.put(article.articleId(), article));
            for (Long articleId : articleIds) {
                CoursewareShareService.PublishedArticle article = byId.get(articleId);
                if (article == null) {
                    throw new CoursewareFailures.NotFound("来源不存在该篇课件：" + articleId);
                }
                boolean allowCopy = Boolean.TRUE.equals(
                        wholeSubject ? policy.get(null) : policy.get(articleId));
                if (!allowCopy) {
                    throw new CoursewareFailures.SourceForbidden("来源未开放该篇的获取权限：" + articleId);
                }
                targets.add(article);
            }
        }

        long sourceVersion = sourceVersion(sourceTeacherId, subject);
        long nextLocalId = nextLocalArticleId(teacherId);
        List<AcquireResult> results = new ArrayList<>();
        for (CoursewareShareService.PublishedArticle article : targets) {
            Long existing = existingCopy(teacherId, sourceTeacherId, article.articleId());
            if (existing != null) {
                long localArticleId = jdbc.sql("SELECT local_article_id FROM public.course_copy WHERE id = :id")
                        .param("id", existing)
                        .query(Long.class)
                        .single();
                results.add(new AcquireResult(existing, localArticleId, article.title(), sourceVersion, true));
                continue;
            }

            byte[] markdown = storage.getBlob(sourceTeacherId, article.contentHash());
            if (markdown == null) {
                throw new CoursewareFailures.NotFound("来源正文缺失：" + article.contentHash());
            }
            storage.putBlob(teacherId, markdown);

            long localArticleId = nextLocalId++;
            String localPath = localPath(targetRootPath, article.logicalPath());
            if (localPathTaken(teacherId, localPath)) {
                throw new CoursewareFailures.TargetExists(
                        "目标路径已存在，请改名或换目录：" + localPath);
            }
            saveDraft(teacherId, localArticleId, new String(markdown, StandardCharsets.UTF_8));
            long copyId = jdbc.sql("""
                    INSERT INTO public.course_copy
                        (owner_account_id, local_article_id, source_teacher_account_id, source_article_id,
                         source_release_id, base_content_hash, base_answer_hash, source_logical_path,
                         local_logical_path, local_title, acquired_via)
                    VALUES (:owner, :localArticleId, :sourceTeacher, :sourceArticle, :sourceRelease,
                            :contentHash, :answerHash, :sourcePath, :localPath, :title, 'copy')
                    RETURNING id
                    """)
                    .param("owner", teacherId)
                    .param("localArticleId", localArticleId)
                    .param("sourceTeacher", sourceTeacherId)
                    .param("sourceArticle", article.articleId())
                    .param("sourceRelease", currentReleaseId(sourceTeacherId, subject))
                    .param("contentHash", article.contentHash())
                    .param("answerHash", article.answerHash())
                    .param("sourcePath", article.logicalPath())
                    .param("localPath", localPath)
                    .param("title", article.title())
                    .query(Long.class)
                    .single();
            log(teacherId, localArticleId, "copy", "{\"copyId\":" + copyId + ",\"sourceTeacher\":" + sourceTeacherId
                    + ",\"sourceArticle\":" + article.articleId() + "}");
            results.add(new AcquireResult(copyId, localArticleId, article.title(), sourceVersion, false));
        }
        return results;
    }

    /** 我的获取关系；{@code hasUpdate} 由 {@code base_content_hash} 与来源当前 hash 比对得出。 */
    @Transactional(readOnly = true)
    public List<CopySummary> myCopies(long teacherId) {
        return jdbc.sql("""
                SELECT c.id, c.local_article_id, c.local_title, c.source_teacher_account_id,
                       acc.username AS source_teacher_name, c.source_article_id, c.source_logical_path,
                       c.local_logical_path, c.base_content_hash, c.status, c.dismissed_hash,
                       ca.content_hash AS source_content_hash
                FROM public.course_copy AS c
                JOIN public.account AS acc ON acc.id = c.source_teacher_account_id
                LEFT JOIN public.course_release_head AS h
                  ON h.teacher_account_id = c.source_teacher_account_id
                LEFT JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.article_id = c.source_article_id
                WHERE c.owner_account_id = :teacherId
                ORDER BY acc.username, c.local_logical_path
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> {
                    String base = rs.getString("base_content_hash");
                    String current = rs.getString("source_content_hash");
                    String dismissed = rs.getString("dismissed_hash");
                    boolean hasUpdate = current != null && !current.equals(base)
                            && !current.equals(dismissed);
                    return new CopySummary(
                            rs.getLong("id"),
                            rs.getLong("local_article_id"),
                            rs.getString("local_title"),
                            rs.getLong("source_teacher_account_id"),
                            rs.getString("source_teacher_name"),
                            rs.getLong("source_article_id"),
                            rs.getString("source_logical_path"),
                            rs.getString("local_logical_path"),
                            base,
                            current,
                            rs.getString("status"),
                            hasUpdate);
                })
                .list();
    }

    private void saveDraft(long teacherId, long articleId, String markdown) {
        jdbc.sql("""
                INSERT INTO public.course_draft (teacher_account_id, article_id, markdown, updated_at)
                VALUES (:teacherId, :articleId, :markdown, now())
                ON CONFLICT (teacher_account_id, article_id) DO UPDATE
                    SET markdown = EXCLUDED.markdown, updated_at = now()
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .param("markdown", markdown)
                .update();
    }

    private boolean localPathTaken(long teacherId, String localPath) {
        return jdbc.sql("""
                SELECT 1 FROM public.course_copy
                WHERE owner_account_id = :owner AND local_logical_path = :localPath
                LIMIT 1
                """)
                .param("owner", teacherId)
                .param("localPath", localPath)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private Long existingCopy(long teacherId, long sourceTeacherId, long sourceArticleId) {        return jdbc.sql("""
                SELECT id FROM public.course_copy
                WHERE owner_account_id = :owner AND source_teacher_account_id = :sourceTeacher
                  AND source_article_id = :sourceArticle
                """)
                .param("owner", teacherId)
                .param("sourceTeacher", sourceTeacherId)
                .param("sourceArticle", sourceArticleId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private long nextLocalArticleId(long teacherId) {
        Long max = jdbc.sql("""
                SELECT max(article_id) FROM (
                    SELECT article_id FROM public.course_article WHERE teacher_account_id = :teacherId
                    UNION ALL
                    SELECT article_id FROM public.course_draft WHERE teacher_account_id = :teacherId
                    UNION ALL
                    SELECT local_article_id FROM public.course_copy WHERE owner_account_id = :teacherId
                ) AS existing
                """)
                .param("teacherId", teacherId)
                .query(Long.class)
                .optional()
                .orElse(null);
        return max == null ? FIRST_LOCAL_ARTICLE_ID : Math.max(max + 1, FIRST_LOCAL_ARTICLE_ID);
    }

    private long currentReleaseId(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT h.release_id FROM public.course_release_head AS h
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query(Long.class)
                .single();
    }

    private long sourceVersion(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT r.version FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query(Integer.class)
                .single();
    }

    private static String localPath(String targetRootPath, String sourcePath) {
        if (targetRootPath == null || targetRootPath.isBlank()) {
            return sourcePath;
        }
        String root = targetRootPath.strip().replace('\\', '/').replaceAll("/+$", "");
        int slash = sourcePath.lastIndexOf('/');
        String fileName = slash < 0 ? sourcePath : sourcePath.substring(slash + 1);
        return root.isEmpty() ? fileName : root + "/" + fileName;
    }

    private void log(long teacherId, long articleId, String action, String detailJson) {
        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, article_id, detail)
                VALUES (:teacherId, :action, :teacherId, :articleId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .param("action", action)
                .param("detail", detailJson)
                .update();
    }

    /** 一次获取的结果。 */
    public record AcquireResult(
            long copyId,
            long localArticleId,
            String title,
            long sourceVersion,
            boolean alreadyExists) {
    }

    /** 我的获取关系（含是否有更新）。 */
    public record CopySummary(
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
    }
}
