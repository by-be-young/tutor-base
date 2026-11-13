package com.tutorbase.courseware.publish;

import java.time.Instant;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareTitles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除一篇课件：把它从「我的课件」以及所有引用它的地方摘掉。
 *
 * <p>已经发布过的篇目会作为新版本（version+1）重新发布，新版本里不再包含这一篇；
 * 旧版本作为历史保留，学员端只认当前版本指针，所以删除立即生效。
 * 同时清理：草稿、答案键、目录行、单篇共享、自己获取的副本、学员内容授权（{@code student.permissions}）。
 * 其他老师从本篇获取的副本不删，只标成 {@code source_unavailable}，避免连带删掉别人的备课内容。
 *
 * <p>学员的作答记录（{@code article_question_submissions} / {@code wrong_questions}）带题干与答案快照，
 * 属于学习历史，不在这里删除。
 */
@Service
public class CoursewareArticleDeletionService {

    private final JdbcClient jdbc;
    private final CoursewarePublisher publisher;

    CoursewareArticleDeletionService(JdbcClient jdbc, CoursewarePublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    public Deleted delete(long teacherAccountId, long articleId) {
        PublishedPosition published = currentPosition(teacherAccountId, articleId);
        DraftPosition draft = currentDraft(teacherAccountId, articleId);
        if (published == null && draft == null) {
            throw new CoursewareFailures.NotFound("课件不存在：" + articleId);
        }

        Long legacyArticleId = published != null ? published.legacyArticleId() : null;
        String subject = published != null ? published.subject() : draft.subject();
        String title = published != null ? published.title() : draft.title();
        String logicalPath = published != null ? published.logicalPath() : draft.logicalPath();

        purgeReferences(teacherAccountId, articleId, legacyArticleId);

        int publishedVersion = 0;
        if (published != null) {
            List<CoursewareArticleInput> remaining = remainingArticles(teacherAccountId, articleId);
            if (remaining.isEmpty()) {
                // 科目里已经没有篇目：直接下线该科目的当前版本指针，页面上不再出现这个科目
                jdbc.sql("""
                        DELETE FROM public.course_release_head
                        WHERE teacher_account_id = :teacherId AND subject = :subject
                        """)
                        .param("teacherId", teacherAccountId)
                        .param("subject", published.subject())
                        .update();
            } else {
                publishedVersion = publisher.publish(teacherAccountId, published.subject(),
                        published.version(), remaining).version();
            }
        }

        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, article_id, detail)
                VALUES (:teacherId, 'delete', :teacherId, :articleId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .param("detail", """
                        {"subject":"%s","title":"%s","logicalPath":"%s","wasPublished":%s,"version":%d}
                        """.formatted(escape(subject), escape(title), escape(logicalPath), published != null,
                        publishedVersion))
                .update();

        return new Deleted(articleId, subject, title, logicalPath, published != null, publishedVersion,
                Instant.now());
    }

    /** 清掉所有指向这篇课件的记录；顺序按外键依赖排列。 */
    private void purgeReferences(long teacherAccountId, long articleId, Long legacyArticleId) {
        jdbc.sql("""
                DELETE FROM public.course_draft
                WHERE teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .update();

        jdbc.sql("DELETE FROM public.article_answer_keys WHERE blog_id = :articleId")
                .param("articleId", articleId)
                .update();

        jdbc.sql("""
                DELETE FROM public.course_asset_ref
                WHERE owner_teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .update();

        jdbc.sql("""
                DELETE FROM public.course_share
                WHERE owner_teacher_account_id = :teacherId AND scope_type = 'article' AND article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .update();

        jdbc.sql("""
                DELETE FROM public.course_copy
                WHERE owner_account_id = :teacherId AND local_article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .update();

        // 别人从这篇获取的副本保留内容，但来源没了，标成不可用
        jdbc.sql("""
                UPDATE public.course_copy
                SET status = 'source_unavailable'
                WHERE source_teacher_account_id = :teacherId AND source_article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .update();

        // 学员内容授权：permissions 是 article_id 整数数组，删除后学员端立即看不到
        jdbc.sql("""
                UPDATE public.student
                SET permissions = array_remove(permissions, cast(:articleId as integer))
                WHERE permissions @> ARRAY[cast(:articleId as integer)]
                """)
                .param("articleId", articleId)
                .update();

        // 目录行（含历史遗留的 legacy_article_id 别名）删除后，认领/排除/放弃记录随外键级联清除
        jdbc.sql("""
                DELETE FROM public.courseware_catalog
                WHERE teacher_account_id = :teacherId
                  AND (article_id IN (:articleId, :legacyArticleId)
                       OR legacy_article_id IN (:articleId, :legacyArticleId))
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .param("legacyArticleId", legacyArticleId == null ? articleId : legacyArticleId)
                .update();
    }

    private PublishedPosition currentPosition(long teacherAccountId, long articleId) {
        return jdbc.sql("""
                SELECT ca.subject, ca.title, ca.logical_path, ca.legacy_article_id, r.version
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                JOIN public.course_article AS ca
                  ON ca.release_id = r.id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new PublishedPosition(
                        rs.getString("subject"),
                        rs.getString("title"),
                        rs.getString("logical_path"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getInt("version")))
                .optional()
                .orElse(null);
    }

    private DraftPosition currentDraft(long teacherAccountId, long articleId) {
        return jdbc.sql("""
                SELECT d.logical_path, d.title, d.markdown
                FROM public.course_draft AS d
                WHERE d.teacher_account_id = :teacherId AND d.article_id = :articleId
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> {
                    String path = rs.getString("logical_path");
                    String subject = path == null || !path.contains("/")
                            ? null
                            : path.substring(0, path.indexOf('/'));
                    String title = rs.getString("title");
                    if (title == null || title.isBlank()) {
                        String markdown = rs.getString("markdown");
                        title = CoursewareTitles.titleOf(markdown == null ? "" : markdown);
                    }
                    return new DraftPosition(subject, title, path);
                })
                .optional()
                .orElse(null);
    }

    /** 当前版本里除这一篇以外的所有篇目，按上一版哈希结转（不重写 blob）。 */
    private List<CoursewareArticleInput> remainingArticles(long teacherAccountId, long articleId) {
        return jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.title, ca.series, ca.logical_path,
                       ca.content_hash, ca.answer_hash, ca.question_count
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id <> :articleId
                ORDER BY ca.article_id
                """)
                .param("teacherId", teacherAccountId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> CoursewareArticleInput.carried(
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

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record PublishedPosition(String subject, String title, String logicalPath, Long legacyArticleId,
            int version) {
    }

    private record DraftPosition(String subject, String title, String logicalPath) {
    }

    /** 删除结果。 */
    public record Deleted(
            long articleId,
            String subject,
            String title,
            String logicalPath,
            boolean wasPublished,
            int version,
            Instant deletedAt) {
    }
}
