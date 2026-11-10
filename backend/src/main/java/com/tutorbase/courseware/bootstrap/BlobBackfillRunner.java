package com.tutorbase.courseware.bootstrap;

import com.tutorbase.courseware.publish.CoursewareArticleInput;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时同步 JAR 内静态课件到数据库 blob：
 * <ul>
 *   <li>blob 缺失 → 写入持久卷</li>
 *   <li>.md 文件 hash 与数据库不一致 → 自动发新版本更新正文</li>
 * </ul>
 * 这样改完 .md 文件 push，部署后自动上线，不需要走网页编辑器。
 */
@Component
@ConditionalOnProperty(name = "tutor.courseware.source", havingValue = "storage")
public class BlobBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobBackfillRunner.class);

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewarePublisher publisher;

    public BlobBackfillRunner(JdbcClient jdbc, CoursewareStorage storage, CoursewarePublisher publisher) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.publisher = publisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SubjectHead> subjects = jdbc.sql("""
                SELECT h.teacher_account_id, h.subject, h.release_id, r.version
                FROM public.course_release_head h
                JOIN public.course_release r ON r.id = h.release_id
                """)
                .query((rs, rowNum) -> new SubjectHead(
                        rs.getLong("teacher_account_id"),
                        rs.getString("subject"),
                        rs.getLong("release_id"),
                        rs.getInt("version")))
                .list();

        for (SubjectHead sh : subjects) {
            syncSubject(sh);
        }
    }

    private void syncSubject(SubjectHead sh) {
        List<ArticleRow> articles = jdbc.sql("""
                SELECT article_id, legacy_article_id, title, series, logical_path,
                       content_hash, answer_hash, question_count
                FROM public.course_article
                WHERE release_id = :releaseId
                ORDER BY logical_path
                """)
                .param("releaseId", sh.releaseId())
                .query((rs, rowNum) -> new ArticleRow(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getInt("question_count")))
                .list();

        List<CoursewareArticleInput> inputs = new ArrayList<>();
        int changed = 0;
        int missingBlob = 0;

        for (ArticleRow a : articles) {
            String resourcePath = "/courseware-static/" + a.logicalPath();
            ClassPathResource resource = new ClassPathResource(resourcePath);

            if (!resource.exists()) {
                // 没有静态文件来源，carry-over
                inputs.add(carry(a));
                continue;
            }

            try {
                byte[] content = resource.getInputStream().readAllBytes();
                String actualHash = LocalFileStorage.sha256(content);

                if (actualHash.equals(a.contentHash())) {
                    // hash 一致，确保 blob 存在
                    if (storage.getBlob(sh.teacherId(), a.contentHash()) == null) {
                        storage.putBlob(sh.teacherId(), content);
                        missingBlob++;
                    }
                    inputs.add(carry(a));
                } else {
                    // hash 变了：用新正文发布
                    List<CoursewareArticleInput.CoursewareAnswerInput> answers = loadAnswers(a.articleId());
                    inputs.add(new CoursewareArticleInput(
                            a.articleId(), a.legacyArticleId(), a.title(), a.series(),
                            a.logicalPath(), new String(content, java.nio.charset.StandardCharsets.UTF_8),
                            answers));
                    changed++;
                }
            } catch (Exception e) {
                LOGGER.warn("同步文章失败: articleId={}, path={}: {}",
                        a.articleId(), a.logicalPath(), e.getMessage());
                inputs.add(carry(a));
            }
        }

        if (changed > 0) {
            try {
                publisher.publish(sh.teacherId(), sh.subject(), sh.version(), inputs);
                LOGGER.info("科目 {} 自动发布新版本: {} 篇文章更新, {} 个补写 blob",
                        sh.subject(), changed, missingBlob);
            } catch (Exception e) {
                LOGGER.error("自动发布 {} 失败: {}", sh.subject(), e.getMessage());
            }
        } else if (missingBlob > 0) {
            LOGGER.info("科目 {} 补写了 {} 个缺失 blob，无内容变更", sh.subject(), missingBlob);
        }
    }

    private CoursewareArticleInput carry(ArticleRow a) {
        return CoursewareArticleInput.carried(
                a.articleId(), a.legacyArticleId(), a.title(), a.series(),
                a.logicalPath(), a.contentHash(), a.answerHash(), a.questionCount());
    }

    private List<CoursewareArticleInput.CoursewareAnswerInput> loadAnswers(long articleId) {
        return jdbc.sql("""
                SELECT question_id, answer_text, auto_grade
                FROM public.article_answer_keys
                WHERE blog_id = :blogId
                ORDER BY question_id
                """)
                .param("blogId", articleId)
                .query((rs, rowNum) -> new CoursewareArticleInput.CoursewareAnswerInput(
                        rs.getString("question_id"), rs.getString("answer_text"), rs.getBoolean("auto_grade")))
                .list();
    }

    private record SubjectHead(long teacherId, String subject, long releaseId, int version) {
    }

    private record ArticleRow(
            long articleId, Long legacyArticleId, String title, String series,
            String logicalPath, String contentHash, String answerHash, int questionCount) {
    }
}
