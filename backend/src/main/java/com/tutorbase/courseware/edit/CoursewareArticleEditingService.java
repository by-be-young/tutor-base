package com.tutorbase.courseware.edit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.layout.CoursewareLayoutService;
import com.tutorbase.courseware.publish.CoursewareArticleInput;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.publish.CoursewareValidator;
import com.tutorbase.courseware.storage.CoursewareStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 已发布课件的直接编辑：保存即发布新版本，不再经过草稿。
 */
@Service
public class CoursewareArticleEditingService {

    private static final long FIRST_ARTICLE_ID = 1000;

    private final JdbcClient jdbc;
    private final CoursewarePublisher publisher;
    private final CoursewareLayoutService layouts;
    private final CoursewareStorage storage;

    CoursewareArticleEditingService(JdbcClient jdbc, CoursewarePublisher publisher,
            CoursewareLayoutService layouts, CoursewareStorage storage) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.layouts = layouts;
        this.storage = storage;
    }

    /** 读取当前发布版本里的正文与元数据。 */
    @Transactional(readOnly = true)
    public ArticleContent read(long teacherId, long articleId) {
        ArticleRow row = currentArticle(teacherId, articleId);
        if (row == null) {
            throw new CoursewareFailures.NotFound("课件不存在或尚未发布：" + articleId);
        }
        byte[] bytes = storage.getBlob(teacherId, row.contentHash());
        if (bytes == null) {
            throw new CoursewareFailures.ContentMissing(
                    "课件 " + articleId + " 的正文文件缺失，请重新保存这一篇以恢复");
        }
        return new ArticleContent(row.articleId(), row.subject(), row.title(), row.series(),
                row.logicalPath(), new String(bytes, StandardCharsets.UTF_8), true);
    }

    /** 保存并立即发布：只替换这一篇，同科目其他篇目按上一版结转。 */
    @Transactional
    public SaveResult update(long teacherId, long articleId, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("正文不能为空"));
        }
        ArticleRow target = currentArticle(teacherId, articleId);
        if (target == null) {
            throw new CoursewareFailures.NotFound("课件不存在或尚未发布：" + articleId);
        }
        validateLayout(markdown);

        Head head = head(teacherId, target.subject());
        String title = firstHeading(markdown);
        if (title.isBlank()) title = target.title();
        if (title == null || title.isBlank()) title = titleOf(target.logicalPath());
        List<CoursewareArticleInput> articles = new ArrayList<>();
        for (Entry entry : entries(head.releaseId())) {
            if (entry.articleId() == articleId) {
                articles.add(new CoursewareArticleInput(entry.articleId(), entry.legacyArticleId(),
                        title, entry.series(), entry.logicalPath(), markdown,
                        answersFor(entry.articleId(), markdown)));
            } else {
                articles.add(CoursewareArticleInput.carried(entry.articleId(), entry.legacyArticleId(),
                        entry.title(), entry.series(), entry.logicalPath(), entry.contentHash(),
                        entry.answerHash(), entry.questionCount()));
            }
        }

        CoursewarePublisher.Published published = publisher.publish(
                teacherId, target.subject(), head.version(), articles);
        jdbc.sql("""
                DELETE FROM public.course_draft
                WHERE teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .update();
        log(teacherId, "publish", target.subject(), published.version(), articleId, "article_update");
        return new SaveResult(articleId, target.subject(), published.version());
    }

    /** 从课件页新建：直接生成一篇已发布课件。 */
    @Transactional
    public Created create(long teacherId, String subject, String title, String logicalPath, String markdown) {
        String course = blankToNull(subject);
        if (course == null) {
            throw new CoursewareFailures.Validation(List.of("科目不能为空"));
        }
        String path = CoursewareValidator.normalizePath(logicalPath);
        if (path.isBlank() || !path.endsWith(".md") || !path.contains("/")) {
            throw new CoursewareFailures.Validation(List.of("课件路径不合法：" + logicalPath));
        }
        if (markdown == null || markdown.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("正文不能为空"));
        }
        validateLayout(markdown);

        Head head = headOrNull(teacherId, course);
        List<Entry> current = head == null ? List.of() : entries(head.releaseId());
        if (current.stream().anyMatch(entry -> entry.logicalPath().equals(path))) {
            throw new CoursewareFailures.Validation(List.of("目标路径已有课件：" + path));
        }

        String articleTitle = blankToNull(title);
        if (articleTitle == null) {
            articleTitle = titleOf(path);
        }
        long articleId = nextArticleId(teacherId);
        List<CoursewareArticleInput> articles = new ArrayList<>();
        for (Entry entry : current) {
            articles.add(CoursewareArticleInput.carried(entry.articleId(), entry.legacyArticleId(),
                    entry.title(), entry.series(), entry.logicalPath(), entry.contentHash(),
                    entry.answerHash(), entry.questionCount()));
        }
        articles.add(new CoursewareArticleInput(articleId, null, articleTitle, course, path, markdown,
                answersFor(null, markdown)));

        CoursewarePublisher.Published published = publisher.publish(
                teacherId, course, head == null ? null : head.version(), articles);
        log(teacherId, "publish", course, published.version(), articleId, "article_create");
        return new Created(articleId, course, published.version());
    }

    private void validateLayout(String markdown) {
        List<String> errors = layouts.validate(markdown);
        if (!errors.isEmpty()) {
            throw new CoursewareFailures.Validation(errors);
        }
    }

    private ArticleRow currentArticle(long teacherId, long articleId) {
        return jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.subject, ca.title, ca.series,
                       ca.logical_path, ca.content_hash, ca.answer_hash, ca.question_count
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                LIMIT 1
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new ArticleRow(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("subject"),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getInt("question_count")))
                .optional()
                .orElse(null);
    }

    private Head head(long teacherId, String subject) {
        return headOrNull(teacherId, subject);
    }

    private Head headOrNull(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT h.release_id, r.version
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new Head(rs.getLong("release_id"), rs.getInt("version")))
                .optional()
                .orElse(null);
    }

    private List<Entry> entries(long releaseId) {
        return jdbc.sql("""
                SELECT article_id, legacy_article_id, title, series, logical_path,
                       content_hash, answer_hash, question_count
                FROM public.course_article
                WHERE release_id = :releaseId
                ORDER BY logical_path
                """)
                .param("releaseId", releaseId)
                .query((rs, rowNum) -> new Entry(
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

    private List<CoursewareArticleInput.CoursewareAnswerInput> answersFor(Long articleId, String markdown) {
        Map<String, CoursewareArticleInput.CoursewareAnswerInput> stored = new LinkedHashMap<>();
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
        for (String id : CoursewareValidator.answerIds(markdown)) {
            answers.add(stored.getOrDefault(id,
                    new CoursewareArticleInput.CoursewareAnswerInput(id, "", false)));
        }
        return answers;
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

    private void log(long teacherId, String action, String subject, int version, long articleId, String mode) {
        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, detail)
                VALUES (:teacherId, :action, :teacherId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherId)
                .param("action", action)
                .param("detail", "{\"mode\":\"" + mode + "\",\"subject\":\"" + escape(subject)
                        + "\",\"version\":" + version + ",\"articleId\":" + articleId + "}")
                .update();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String titleOf(String logicalPath) {
        int slash = logicalPath.lastIndexOf('/');
        String name = slash < 0 ? logicalPath : logicalPath.substring(slash + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private static String firstHeading(String markdown) {
        for (String line : (markdown == null ? "" : markdown).split("\n")) {
            if (line.startsWith("# ")) return line.substring(2).trim();
        }
        return "";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Head(long releaseId, int version) {
    }

    private record ArticleRow(
            long articleId, Long legacyArticleId, String subject, String title, String series,
            String logicalPath, String contentHash, String answerHash, int questionCount) {
    }

    private record Entry(
            long articleId, Long legacyArticleId, String title, String series,
            String logicalPath, String contentHash, String answerHash, int questionCount) {
    }

    public record ArticleContent(
            long articleId, String subject, String title, String series,
            String logicalPath, String markdown, boolean published) {
    }

    public record SaveResult(long articleId, String subject, int version) {
    }

    public record Created(long articleId, String subject, int version) {
    }
}
