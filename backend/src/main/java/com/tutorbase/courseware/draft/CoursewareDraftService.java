package com.tutorbase.courseware.draft;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.layout.CoursewareLayoutService;
import com.tutorbase.courseware.publish.CoursewareArticleInput;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.publish.CoursewareValidator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在线编辑器的草稿与发布。
 *
 * <p>草稿只存在 {@code course_draft}，学员端看不到；点"发布"才把草稿与当前版本的其他篇目
 * 一起组成一次 release（整科发布语义不变，见设计稿 §7.1）。答案键沿用
 * {@code article_answer_keys}，缺失时按空答案发布（前端随后可在批阅/答案面板补齐）。
 */
@Service
public class CoursewareDraftService {

    private static final long FIRST_ARTICLE_ID = 1000;

    private final JdbcClient jdbc;
    private final CoursewarePublisher publisher;
    private final CoursewareLayoutService layouts;
    private final com.tutorbase.courseware.storage.CoursewareStorage storage;

    CoursewareDraftService(JdbcClient jdbc, CoursewarePublisher publisher, CoursewareLayoutService layouts,
            com.tutorbase.courseware.storage.CoursewareStorage storage) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.layouts = layouts;
        this.storage = storage;
    }

    /** 我的草稿列表（含未发布的新建篇目）。 */
    @Transactional(readOnly = true)
    public List<DraftSummary> list(long teacherId) {
        return jdbc.sql("""
                SELECT d.article_id, d.updated_at, length(d.markdown) AS size,
                       ca.title, ca.series, ca.logical_path, ca.subject,
                       h.release_id IS NOT NULL AS published
                FROM public.course_draft AS d
                LEFT JOIN public.course_release_head AS h
                  ON h.teacher_account_id = d.teacher_account_id
                LEFT JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = d.teacher_account_id
                 AND ca.article_id = d.article_id
                WHERE d.teacher_account_id = :teacherId
                ORDER BY d.updated_at DESC
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new DraftSummary(
                        rs.getLong("article_id"),
                        rs.getString("title"),
                        rs.getString("subject"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getInt("size"),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getBoolean("published")))
                .list();
    }

    /** 取一篇草稿；没有草稿时回落到当前发布版本的正文（只读查看）。 */
    @Transactional(readOnly = true)
    public Draft get(long teacherId, long articleId) {
        String markdown = jdbc.sql("""
                SELECT markdown FROM public.course_draft
                WHERE teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query(String.class)
                .optional()
                .orElse(null);
        if (markdown != null) {
            return new Draft(articleId, markdown, true);
        }
        String published = publishedMarkdown(teacherId, articleId);
        if (published == null) {
            throw new CoursewareFailures.NotFound("草稿与已发布版本都不存在：" + articleId);
        }
        return new Draft(articleId, published, false);
    }

    /** 新建课件骨架（不落库，rticleId=0 表示尚未分配；前端保存时走 POST）。 */
    public Draft skeleton(String title, String layoutId) {
        return new Draft(0L, CoursewareLayoutService.skeleton(title, layoutId), false);
    }

    /**
     * 保存草稿。{@code articleId} 为空时在该老师命名空间内分配新 id。
     *
     * <p>草稿不做发布校验（允许半成品），但会在返回值里带上预检结果，前端可提前提示。
     */
    @Transactional
    public Draft save(long teacherId, Long articleId, Long legacyArticleId, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("正文不能为空"));
        }
        long id = articleId == null ? nextArticleId(teacherId) : articleId;
        jdbc.sql("""
                INSERT INTO public.course_draft (teacher_account_id, article_id, markdown, updated_at)
                VALUES (:teacherId, :articleId, :markdown, now())
                ON CONFLICT (teacher_account_id, article_id) DO UPDATE
                    SET markdown = EXCLUDED.markdown, updated_at = now()
                """)
                .param("teacherId", teacherId)
                .param("articleId", id)
                .param("markdown", markdown)
                .update();
        return new Draft(id, markdown, true);
    }

    @Transactional
    public void delete(long teacherId, long articleId) {
        int removed = jdbc.sql("""
                DELETE FROM public.course_draft
                WHERE teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .update();
        if (removed == 0) {
            throw new CoursewareFailures.NotFound("草稿不存在：" + articleId);
        }
    }

    /** 草稿预检：与发布同一套规则（含布局校验），失败不阻塞保存。 */
    @Transactional(readOnly = true)
    public List<String> precheck(long teacherId, String markdown) {
        List<String> errors = new ArrayList<>(layouts.validate(markdown));
        if (!errors.isEmpty()) {
            return errors;
        }
        CoursewareArticleInput input = new CoursewareArticleInput(
                null, null, "预检", "预检", "预检/预检.md", markdown, answersFor(null, markdown));
        errors.addAll(CoursewareValidator.validate(input, 1_048_576));
        return errors;
    }

    /**
     * 发布指定科目：当前版本的全部篇目 + 该科目下所有草稿覆盖。
     *
     * @param expectVersion 与当前版本不一致返回 409
     */
    @Transactional
    public CoursewarePublisher.Published publish(long teacherId, String subject, Integer expectVersion) {
        List<PublishedRow> current = jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.title, ca.series, ca.logical_path, ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                ORDER BY ca.logical_path
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new PublishedRow(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash")))
                .list();

        List<DraftRow> drafts = jdbc.sql("""
                SELECT d.article_id, d.markdown, ca.article_id AS published_id,
                       ca.legacy_article_id, ca.title, ca.series, ca.logical_path
                FROM public.course_draft AS d
                LEFT JOIN public.course_release_head AS h
                  ON h.teacher_account_id = d.teacher_account_id
                LEFT JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = d.teacher_account_id
                 AND ca.article_id = d.article_id
                WHERE d.teacher_account_id = :teacherId
                  AND (ca.subject = :subject OR ca.article_id IS NULL)
                ORDER BY d.article_id
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new DraftRow(
                        rs.getLong("article_id"),
                        rs.getString("markdown"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path")))
                .list();

        if (current.isEmpty() && drafts.isEmpty()) {
            throw new CoursewareFailures.NotFound("该科目还没有任何课件：" + subject);
        }

        List<CoursewareArticleInput> articles = new ArrayList<>();
        for (PublishedRow row : current) {
            String markdown = draftMarkdownOf(drafts, row.articleId());
            if (markdown == null) {
                markdown = publishedMarkdown(teacherId, row.articleId());
            }
            if (markdown == null) {
                continue;
            }
            articles.add(new CoursewareArticleInput(row.articleId(), row.legacyArticleId(), row.title(),
                    row.series(), row.logicalPath(), markdown, answersFor(row.articleId(), markdown)));
        }
        for (DraftRow draft : drafts) {
            if (containsArticle(articles, draft.articleId())) {
                continue;
            }
            String title = draft.title() == null ? "草稿 " + draft.articleId() : draft.title();
            String series = draft.series() == null ? subject : draft.series();
            String path = draft.logicalPath() == null
                    ? subject + "/" + title + ".md"
                    : draft.logicalPath();
            articles.add(new CoursewareArticleInput(draft.articleId(), draft.legacyArticleId(), title,
                    series, path, draft.markdown(), answersFor(draft.articleId(), draft.markdown())));
        }

        List<String> layoutErrors = new ArrayList<>();
        for (CoursewareArticleInput article : articles) {
            for (String error : layouts.validate(article.markdown())) {
                layoutErrors.add(article.logicalPath() + " → " + error);
            }
        }
        if (!layoutErrors.isEmpty()) {
            throw new CoursewareFailures.Validation(layoutErrors);
        }
        return publisher.publish(teacherId, subject, expectVersion, articles);
    }

    private boolean containsArticle(List<CoursewareArticleInput> articles, long articleId) {
        return articles.stream().anyMatch(article ->
                article.articleId() != null && article.articleId() == articleId);
    }

    private String draftMarkdownOf(List<DraftRow> drafts, long articleId) {
        return drafts.stream()
                .filter(draft -> draft.articleId() == articleId)
                .map(DraftRow::markdown)
                .findFirst()
                .orElse(null);
    }

    private List<CoursewareArticleInput.CoursewareAnswerInput> answersFor(Long articleId, String markdown) {
        List<String> ids = CoursewareValidator.answerIds(markdown);
        java.util.Map<String, CoursewareArticleInput.CoursewareAnswerInput> stored = new java.util.LinkedHashMap<>();
        if (articleId != null) {
            jdbc.sql("""
                    SELECT question_id, answer_text, auto_grade
                    FROM public.article_answer_keys
                    WHERE blog_id = :blogId
                    """)
                    .param("blogId", articleId)
                    .query((rs, rowNum) -> new CoursewareArticleInput.CoursewareAnswerInput(
                            rs.getString("question_id"), rs.getString("answer_text"), rs.getBoolean("auto_grade")))
                    .list()
                    .forEach(answer -> stored.put(answer.questionId(), answer));
        }
        List<CoursewareArticleInput.CoursewareAnswerInput> answers = new ArrayList<>();
        for (String id : ids) {
            answers.add(stored.getOrDefault(id,
                    new CoursewareArticleInput.CoursewareAnswerInput(id, "", false)));
        }
        return answers;
    }

    private String publishedMarkdown(long teacherId, long articleId) {
        String hash = jdbc.sql("""
                SELECT ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query(String.class)
                .optional()
                .orElse(null);
        if (hash == null) {
            return null;
        }
        byte[] bytes = storage.getBlob(teacherId, hash);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private long nextArticleId(long teacherId) {
        Long max = jdbc.sql("""
                SELECT max(id) FROM (
                    SELECT article_id AS id FROM public.course_article WHERE teacher_account_id = :teacherId
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
        return max == null ? FIRST_ARTICLE_ID : Math.max(max + 1, FIRST_ARTICLE_ID);
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
                .param("detail", detailJson == null ? "{}" : detailJson)
                .update();
    }

    private record PublishedRow(
            long articleId, Long legacyArticleId, String title, String series,
            String logicalPath, String contentHash) {
    }

    private record DraftRow(
            long articleId, String markdown, Long legacyArticleId,
            String title, String series, String logicalPath) {
    }

    /** 一篇草稿。 */
    public record Draft(long articleId, String markdown, boolean hasDraft) {
    }

    /** 草稿列表项。 */
    public record DraftSummary(
            long articleId,
            String title,
            String subject,
            String series,
            String logicalPath,
            int size,
            Instant updatedAt,
            boolean published) {
    }
}
