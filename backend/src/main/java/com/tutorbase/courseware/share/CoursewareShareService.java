package com.tutorbase.courseware.share;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tutorbase.courseware.CoursewareFailures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课件分享：老师把整科或单篇开放给其他老师，形成资源目录。
 *
 * <p>分享只描述可见范围，不复制内容；{@code allow_copy} 决定能否获取，{@code allow_preview}
 * 决定能否预览（设计稿 §7.3 / §8.2）。整科分享用 {@code article_id IS NULL} 表示。
 */
@Service
public class CoursewareShareService {

    private final JdbcClient jdbc;

    CoursewareShareService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 新建或刷新分享；同一 (老师, 科目, 单篇) 重复提交时更新权限而不是堆行。
     *
     * @param scope      {@code subject} 整科 / {@code article} 指定单篇
     * @param articleIds scope=article 时必填
     */
    @Transactional
    public List<Share> share(
            long teacherId, String subject, String scope, List<Long> articleIds,
            boolean allowPreview, boolean allowCopy) {
        if (subject == null || subject.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("subject 不能为空"));
        }
        List<Long> targets = new ArrayList<>();
        if ("subject".equals(scope)) {
            targets.add(null);
        } else if ("article".equals(scope)) {
            if (articleIds == null || articleIds.isEmpty()) {
                throw new CoursewareFailures.Validation(List.of("单篇分享必须提供 articleIds"));
            }
            for (Long articleId : new LinkedHashSet<>(articleIds)) {
                if (articleId == null || articleId <= 0) {
                    throw new CoursewareFailures.Validation(List.of("articleIds 必须是正整数"));
                }
                targets.add(articleId);
            }
        } else {
            throw new CoursewareFailures.Validation(List.of("scope 只能是 subject 或 article"));
        }

        List<Share> saved = new ArrayList<>();
        for (Long articleId : targets) {
            if (!isPublished(teacherId, subject, articleId)) {
                throw new CoursewareFailures.Validation(List.of(
                        "只能分享自己已发布的课件：" + subject + (articleId == null ? "" : " / " + articleId)));
            }
            refreshOrInsert(teacherId, subject, articleId, allowPreview, allowCopy);
            saved.addAll(activeShares(teacherId, subject, articleId));
        }
        log(teacherId, "share", "{\"subject\":\"" + escape(subject) + "\",\"scope\":\"" + escape(scope)
                + "\",\"count\":" + targets.size() + "}");
        return saved;
    }

    /** 我发出的分享；默认只列生效中的。 */
    @Transactional(readOnly = true)
    public List<Share> myShares(long teacherId, boolean includeRevoked) {
        return jdbc.sql("""
                SELECT id, owner_teacher_account_id, scope_type, subject, article_id,
                       allow_preview, allow_copy, revoked_at
                FROM public.course_share
                WHERE owner_teacher_account_id = :teacherId
                  AND (cast(:includeRevoked as boolean) OR revoked_at IS NULL)
                ORDER BY subject, article_id NULLS FIRST, id
                """)
                .param("teacherId", teacherId)
                .param("includeRevoked", includeRevoked)
                .query((rs, rowNum) -> new Share(
                        rs.getLong("id"),
                        rs.getLong("owner_teacher_account_id"),
                        rs.getString("scope_type"),
                        rs.getString("subject"),
                        rs.getObject("article_id", Long.class),
                        rs.getBoolean("allow_preview"),
                        rs.getBoolean("allow_copy"),
                        rs.getObject("revoked_at") != null))
                .list();
    }

    /** 撤回分享。 */
    @Transactional
    public void revoke(long teacherId, long shareId) {
        int updated = jdbc.sql("""
                UPDATE public.course_share
                SET revoked_at = now(), updated_at = now()
                WHERE id = :shareId AND owner_teacher_account_id = :teacherId AND revoked_at IS NULL
                """)
                .param("shareId", shareId)
                .param("teacherId", teacherId)
                .update();
        if (updated == 0) {
            throw new CoursewareFailures.NotFound("分享不存在或已撤回：" + shareId);
        }
        log(teacherId, "revoke_share", "{\"shareId\":" + shareId + "}");
    }

    /** 资源目录：其他老师已主动分享的科目（只列真正有内容的）。 */
    @Transactional(readOnly = true)
    public List<SharedSubject> resources(long teacherId) {
        List<ResourceRow> rows = jdbc.sql("""
                SELECT s.owner_teacher_account_id AS teacher_id, acc.username AS teacher_name, s.subject,
                       max(CASE WHEN s.scope_type = 'subject' THEN 1 ELSE 0 END) AS whole_subject,
                       count(*) FILTER (WHERE s.scope_type = 'article') AS article_shares,
                       bool_and(s.allow_copy) AS allow_copy
                FROM public.course_share AS s
                JOIN public.account AS acc ON acc.id = s.owner_teacher_account_id
                WHERE s.revoked_at IS NULL AND s.owner_teacher_account_id <> :teacherId
                GROUP BY s.owner_teacher_account_id, acc.username, s.subject
                ORDER BY acc.username, s.subject
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new ResourceRow(
                        rs.getLong("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getString("subject"),
                        rs.getInt("whole_subject") == 1,
                        rs.getInt("article_shares"),
                        rs.getBoolean("allow_copy")))
                .list();

        List<SharedSubject> result = new ArrayList<>();
        for (ResourceRow row : rows) {
            int count = row.wholeSubject()
                    ? publishedArticleCount(row.teacherId(), row.subject())
                    : row.articleSharingCount();
            if (count > 0) {
                result.add(new SharedSubject(row.teacherId(), row.teacherName(), row.subject(),
                        row.wholeSubject(), count, row.allowCopy()));
            }
        }
        return result;
    }

    /** 展开某位老师某科目的共享树（只含被分享的篇目，保留完整逻辑路径）。 */
    @Transactional(readOnly = true)
    public List<SharedArticle> tree(long teacherId, long sourceTeacherId, String subject) {
        if (sourceTeacherId == teacherId) {
            throw new CoursewareFailures.Forbidden("资源目录只列出其他老师的共享课件");
        }
        Map<Long, Boolean> policy = sharePolicy(sourceTeacherId, subject);
        if (policy.isEmpty()) {
            throw new CoursewareFailures.NotFound("该科目未分享：" + sourceTeacherId + "/" + subject);
        }
        boolean wholeSubject = policy.containsKey(null);
        List<SharedArticle> visible = new ArrayList<>();
        for (PublishedArticle article : publishedArticles(sourceTeacherId, subject)) {
            boolean shared = wholeSubject || policy.containsKey(article.articleId());
            if (!shared) {
                continue;
            }
            boolean allowCopy = Boolean.TRUE.equals(
                    wholeSubject ? policy.get(null) : policy.get(article.articleId()));
            visible.add(new SharedArticle(article.articleId(), article.legacyArticleId(), article.title(),
                    article.series(), article.logicalPath(), article.contentHash(),
                    article.questionCount(), allowCopy));
        }
        return visible;
    }

    /** 分享策略：key {@code null} 表示整科，value 为是否允许获取。 */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> sharePolicy(long sourceTeacherId, String subject) {
        Map<Long, Boolean> policy = new LinkedHashMap<>();
        List<SharePolicyRow> rows = jdbc.sql("""
                SELECT article_id, allow_copy
                FROM public.course_share
                WHERE owner_teacher_account_id = :teacherId AND subject = :subject AND revoked_at IS NULL
                """)
                .param("teacherId", sourceTeacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new SharePolicyRow(
                        rs.getObject("article_id", Long.class), rs.getBoolean("allow_copy")))
                .list();
        for (SharePolicyRow row : rows) {
            policy.put(row.articleId(), row.allowCopy());
        }
        return policy;
    }

    /** 某位老师某科目当前发布版本里的全部篇目。 */
    @Transactional(readOnly = true)
    public List<PublishedArticle> publishedArticles(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.title, ca.series, ca.logical_path,
                       ca.content_hash, ca.answer_hash, ca.question_count
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                ORDER BY ca.logical_path
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new PublishedArticle(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getInt("question_count")))
                .list();
    }

    private void refreshOrInsert(
            long teacherId, String subject, Long articleId, boolean allowPreview, boolean allowCopy) {
        int updated = jdbc.sql("""
                UPDATE public.course_share
                SET allow_preview = :allowPreview, allow_copy = :allowCopy,
                    revoked_at = NULL, updated_at = now()
                WHERE owner_teacher_account_id = :teacherId
                  AND subject = :subject
                  AND article_id IS NOT DISTINCT FROM cast(:articleId as bigint)
                  AND revoked_at IS NULL
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .param("articleId", articleId)
                .param("allowPreview", allowPreview)
                .param("allowCopy", allowCopy)
                .update();
        if (updated > 0) {
            return;
        }
        jdbc.sql("""
                INSERT INTO public.course_share
                    (owner_teacher_account_id, scope_type, subject, article_id, allow_preview, allow_copy)
                VALUES (:teacherId, :scope, :subject, :articleId, :allowPreview, :allowCopy)
                """)
                .param("teacherId", teacherId)
                .param("scope", articleId == null ? "subject" : "article")
                .param("subject", subject)
                .param("articleId", articleId)
                .param("allowPreview", allowPreview)
                .param("allowCopy", allowCopy)
                .update();
    }

    private List<Share> activeShares(long teacherId, String subject, Long articleId) {
        return jdbc.sql("""
                SELECT id, owner_teacher_account_id, scope_type, subject, article_id,
                       allow_preview, allow_copy
                FROM public.course_share
                WHERE owner_teacher_account_id = :teacherId AND subject = :subject
                  AND article_id IS NOT DISTINCT FROM cast(:articleId as bigint) AND revoked_at IS NULL
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new Share(
                        rs.getLong("id"),
                        rs.getLong("owner_teacher_account_id"),
                        rs.getString("scope_type"),
                        rs.getString("subject"),
                        rs.getObject("article_id", Long.class),
                        rs.getBoolean("allow_preview"),
                        rs.getBoolean("allow_copy"),
                        false))
                .list();
    }

    private boolean isPublished(long teacherId, String subject, Long articleId) {
        Integer found = jdbc.sql("""
                SELECT 1
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                  AND (cast(:articleId as bigint) IS NULL OR ca.article_id = :articleId)
                LIMIT 1
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .param("articleId", articleId)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return found != null;
    }

    private int publishedArticleCount(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT count(*)
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query(Integer.class)
                .single();
    }

    private void log(long actorId, String action, String detailJson) {
        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, detail)
                VALUES (:actorId, :action, :actorId, cast(:detail as jsonb))
                """)
                .param("actorId", actorId)
                .param("action", action)
                .param("detail", detailJson)
                .update();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ResourceRow(
            long teacherId, String teacherName, String subject,
            boolean wholeSubject, int articleSharingCount, boolean allowCopy) {
    }

    private record SharePolicyRow(Long articleId, boolean allowCopy) {
    }

    /** 一条分享记录。 */
    public record Share(
            long shareId,
            long teacherId,
            String scope,
            String subject,
            Long articleId,
            boolean allowPreview,
            boolean allowCopy,
            boolean revoked) {
    }

    /** 资源目录里的一个老师科目。 */
    public record SharedSubject(
            long teacherId,
            String teacherName,
            String subject,
            boolean wholeSubject,
            int articleCount,
            boolean allowCopy) {
    }

    /** 共享树里的一篇。 */
    public record SharedArticle(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            int questionCount,
            boolean allowCopy) {
    }

    /** 当前发布版本里的一篇（复制来源）。 */
    public record PublishedArticle(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            String answerHash,
            int questionCount) {
    }
}
