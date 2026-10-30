package com.tutorbase.courseware.publish;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布：校验 → 计算哈希 → 写 blob 与不可变 manifest → 落库并切换当前版本指针。
 *
 * <p>同一老师同一科目串行发布：先锁 {@code course_release_head} 行（首版无行时靠唯一约束兜底）。
 * 失败整体回滚，不会留下半个版本。
 */
@Service
public class CoursewarePublisher {

    private static final int FIRST_ARTICLE_ID = 1000;

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewareProperties properties;
    private final ObjectMapper objectMapper;

    CoursewarePublisher(
            JdbcClient jdbc,
            CoursewareStorage storage,
            CoursewareProperties properties,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Published publish(long teacherAccountId, String subject, Integer expectVersion, List<CoursewareArticleInput> articles) {
        List<String> errors = new ArrayList<>();
        for (CoursewareArticleInput article : articles) {
            errors.addAll(CoursewareValidator.validate(article, properties.maxArticleBytes()));
        }
        if (!errors.isEmpty()) {
            throw new CoursewareFailures.Validation(errors);
        }

        long currentReleaseId = 0;
        int currentVersion = 0;
        var head = jdbc.sql("""
                SELECT h.release_id, r.version
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                FOR UPDATE
                """)
                .param("teacherId", teacherAccountId)
                .param("subject", subject)
                .query((rs, rowNum) -> new long[] { rs.getLong("release_id"), rs.getLong("version") })
                .optional();
        if (head.isPresent()) {
            currentReleaseId = head.get()[0];
            currentVersion = (int) head.get()[1];
        }
        if (expectVersion != null && expectVersion != currentVersion) {
            throw new CoursewareFailures.VersionConflict(
                    "当前版本为 " + currentVersion + "，与 expectVersion=" + expectVersion + " 不一致");
        }
        int nextVersion = currentVersion + 1;

        List<Map<String, Object>> manifestArticles = new ArrayList<>();
        List<PreparedArticle> prepared = new ArrayList<>();
        long nextArticleId = nextArticleId(teacherAccountId);

        for (CoursewareArticleInput article : articles) {
            long articleId = article.articleId() != null ? article.articleId() : nextArticleId++;
            byte[] markdown = article.markdown().getBytes(StandardCharsets.UTF_8);
            String contentHash = storage.putBlob(teacherAccountId, markdown);
            String answerHash = LocalFileStorage.sha256(answerBytes(article));

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("articleId", articleId);
            entry.put("legacyArticleId", article.legacyArticleId());
            entry.put("title", article.title());
            entry.put("series", article.series());
            entry.put("logicalPath", CoursewareValidator.normalizePath(article.logicalPath()));
            entry.put("contentHash", contentHash);
            entry.put("answerHash", answerHash);
            entry.put("questionCount", answerCount(article));
            manifestArticles.add(entry);
            prepared.add(new PreparedArticle(articleId, article.legacyArticleId(), article.title(),
                    article.series(), CoursewareValidator.normalizePath(article.logicalPath()),
                    contentHash, answerHash, answerCount(article)));
        }

        String contentHash = LocalFileStorage.sha256(writeJson(manifestArticles));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("teacherId", teacherAccountId);
        manifest.put("teacherName", teacherName(teacherAccountId));
        manifest.put("subject", subject);
        manifest.put("version", nextVersion);
        manifest.put("publishedAt", Instant.now().toString());
        manifest.put("contentHash", contentHash);
        manifest.put("articles", manifestArticles);
        byte[] manifestBytes = writeJson(manifest);
        String manifestPath = "manifests/" + teacherAccountId + "/" + subject + "/v" + nextVersion + ".json";
        String manifestHash = LocalFileStorage.sha256(manifestBytes);
        storage.putManifest(teacherAccountId, subject, nextVersion, manifestBytes);

        long releaseId;
        try {
            releaseId = jdbc.sql("""
                    INSERT INTO public.course_release
                        (teacher_account_id, subject, version, manifest_path, manifest_hash)
                    VALUES (:teacherId, :subject, :version, :manifestPath, :manifestHash)
                    RETURNING id
                    """)
                    .param("teacherId", teacherAccountId)
                    .param("subject", subject)
                    .param("version", nextVersion)
                    .param("manifestPath", manifestPath)
                    .param("manifestHash", manifestHash)
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException conflict) {
            throw new CoursewareFailures.VersionConflict("版本 " + nextVersion + " 已被并发发布，请重试");
        }

        for (PreparedArticle article : prepared) {
            jdbc.sql("""
                    INSERT INTO public.course_article
                        (release_id, teacher_account_id, article_id, legacy_article_id, subject, title, series,
                         logical_path, content_hash, answer_hash, question_count)
                    VALUES (:releaseId, :teacherId, :articleId, :legacyArticleId, :subject, :title, :series,
                            :logicalPath, :contentHash, :answerHash, :questionCount)
                    """)
                    .param("releaseId", releaseId)
                    .param("teacherId", teacherAccountId)
                    .param("articleId", article.articleId())
                    .param("legacyArticleId", article.legacyArticleId())
                    .param("subject", subject)
                    .param("title", article.title())
                    .param("series", article.series())
                    .param("logicalPath", article.logicalPath())
                    .param("contentHash", article.contentHash())
                    .param("answerHash", article.answerHash())
                    .param("questionCount", article.questionCount())
                    .update();
        }

        jdbc.sql("""
                INSERT INTO public.course_release_head (teacher_account_id, subject, release_id, updated_at)
                VALUES (:teacherId, :subject, :releaseId, now())
                ON CONFLICT (teacher_account_id, subject) DO UPDATE
                    SET release_id = EXCLUDED.release_id,
                        updated_at = now()
                """)
                .param("teacherId", teacherAccountId)
                .param("subject", subject)
                .param("releaseId", releaseId)
                .update();

        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, detail)
                VALUES (:teacherId, 'publish', :teacherId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherAccountId)
                .param("detail", """
                        {"subject":"%s","version":%d,"articles":%d,"previousReleaseId":%d}
                        """.formatted(escape(subject), nextVersion, prepared.size(), currentReleaseId))
                .update();

        return new Published(teacherAccountId, subject, nextVersion, manifestHash, prepared.size(), Instant.now());
    }

    private long nextArticleId(long teacherAccountId) {
        Long max = jdbc.sql("""
                SELECT max(article_id) FROM (
                    SELECT article_id FROM public.course_article WHERE teacher_account_id = :teacherId
                    UNION ALL
                    SELECT article_id FROM public.courseware_catalog WHERE teacher_account_id = :teacherId
                ) AS existing
                """)
                .param("teacherId", teacherAccountId)
                .query(Long.class)
                .optional()
                .orElse(null);
        return max == null ? FIRST_ARTICLE_ID : Math.max(max + 1, FIRST_ARTICLE_ID);
    }

    private String teacherName(long teacherAccountId) {
        return jdbc.sql("SELECT username FROM public.account WHERE id = :id")
                .param("id", teacherAccountId)
                .query(String.class)
                .optional()
                .orElse("老师 " + teacherAccountId);
    }

    private byte[] answerBytes(CoursewareArticleInput article) {
        List<Map<String, Object>> answers = new ArrayList<>();
        if (article.answers() != null) {
            for (CoursewareArticleInput.CoursewareAnswerInput answer : article.answers()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("questionId", answer.questionId());
                entry.put("answer", answer.answer());
                entry.put("autoGrade", answer.autoGrade());
                answers.add(entry);
            }
        }
        return writeJson(answers);
    }

    private static int answerCount(CoursewareArticleInput article) {
        return article.answers() == null ? 0 : article.answers().size();
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化发布清单失败", exception);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record PreparedArticle(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            String answerHash,
            int questionCount) {
    }

    /** 发布结果。 */
    public record Published(
            long teacherId,
            String subject,
            int version,
            String contentHash,
            int articleCount,
            Instant publishedAt) {
    }
}
