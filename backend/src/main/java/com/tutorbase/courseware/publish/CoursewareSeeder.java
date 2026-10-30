package com.tutorbase.courseware.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 存量课件种子导入：把 {@code public/data/articles.json} 索引 + {@code public/articles} 文件
 * 发布为初始老师的第一版 release（{@code legacy_article_id} 保留 1–44）。
 *
 * <p>默认关闭，仅在一次性迁移时通过 {@code TUTOR_COURSEWARE_SEED_ENABLED=true} 打开；
 * 已有同名科目当前版本时默认跳过，避免重复发布。
 */
@Component
@ConditionalOnProperty(prefix = "tutor.courseware.seed", name = "enabled", havingValue = "true")
class CoursewareSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoursewareSeeder.class);

    private final JdbcClient jdbc;
    private final CoursewarePublisher publisher;
    private final ObjectMapper objectMapper;
    private final Path index;
    private final Path root;
    private final String teacherUsername;
    private final boolean overwrite;

    CoursewareSeeder(
            JdbcClient jdbc,
            CoursewarePublisher publisher,
            ObjectMapper objectMapper,
            org.springframework.core.env.Environment environment) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.index = Path.of(environment.getRequiredProperty("tutor.courseware.seed.index"));
        this.root = Path.of(environment.getRequiredProperty("tutor.courseware.seed.root"));
        this.teacherUsername = environment.getProperty("tutor.courseware.seed.teacher-username", "young");
        this.overwrite = Boolean.parseBoolean(environment.getProperty("tutor.courseware.seed.overwrite", "false"));
    }

    @Override
    public void run(ApplicationArguments args) {
        long teacherId = teacherAccountId();
        Map<String, List<CoursewareArticleInput>> bySubject = inputsBySubject();
        for (Map.Entry<String, List<CoursewareArticleInput>> entry : bySubject.entrySet()) {
            String subject = entry.getKey();
            if (!overwrite && hasRelease(teacherId, subject)) {
                log.info("课件种子导入跳过 {}：该科目已有发布版本", subject);
                continue;
            }
            CoursewarePublisher.Published published =
                    publisher.publish(teacherId, subject, null, entry.getValue());
            log.info("课件种子导入完成 {}：v{}，{} 篇，{}",
                    subject, published.version(), published.articleCount(), published.contentHash());
        }
    }

    private long teacherAccountId() {
        return jdbc.sql("SELECT id FROM public.account WHERE username = :username AND status = 'active'")
                .param("username", teacherUsername)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("找不到初始老师账号：" + teacherUsername));
    }

    private boolean hasRelease(long teacherId, String subject) {
        return jdbc.sql("SELECT 1 FROM public.course_release_head "
                        + "WHERE teacher_account_id = :teacherId AND subject = :subject")
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    Map<String, List<CoursewareArticleInput>> inputsBySubject() {
        Map<String, List<CoursewareArticleInput>> bySubject = new LinkedHashMap<>();
        for (SeedArticle article : readIndex(index, objectMapper)) {
            CoursewareArticleInput input = toInput(article);
            bySubject.computeIfAbsent(article.series(), key -> new ArrayList<>()).add(input);
        }
        return bySubject;
    }

    private CoursewareArticleInput toInput(SeedArticle article) {
        Path file = root.resolve(article.path().replace('\\', '/'));
        String markdown;
        try {
            markdown = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取存量课件失败：" + file, exception);
        }
        List<CoursewareArticleInput.CoursewareAnswerInput> answers = answersFor(article.id(), markdown);
        return new CoursewareArticleInput(article.id(), article.id(), article.title(), article.series(),
                article.path(), markdown, answers);
    }

    private List<CoursewareArticleInput.CoursewareAnswerInput> answersFor(long articleId, String markdown) {
        Map<String, CoursewareArticleInput.CoursewareAnswerInput> stored = new LinkedHashMap<>();
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

        List<CoursewareArticleInput.CoursewareAnswerInput> answers = new ArrayList<>();
        for (String questionId : CoursewareValidator.answerIds(markdown)) {
            CoursewareArticleInput.CoursewareAnswerInput answer = stored.get(questionId);
            if (answer == null) {
                log.warn("课件 {} 的第 {} 题暂无答案键，先以空答案发布", articleId, questionId);
                answer = new CoursewareArticleInput.CoursewareAnswerInput(questionId, "", false);
            }
            answers.add(answer);
        }
        return answers;
    }

    /** 解析 {@code articles.json}；只保留字段齐全的条目。 */
    static List<SeedArticle> readIndex(Path index, ObjectMapper objectMapper) {
        byte[] json;
        try {
            json = Files.readAllBytes(index);
        } catch (IOException exception) {
            throw new IllegalStateException("读取课件索引失败：" + index, exception);
        }
        List<SeedArticle> articles = new ArrayList<>();
        for (var node : objectMapper.readTree(json)) {
            String path = node.path("path").asString("");
            String series = node.path("series").asString("");
            String title = node.path("title").asString("");
            long id = node.path("id").asLong(0);
            if (id <= 0 || path.isBlank() || series.isBlank() || title.isBlank()) {
                continue;
            }
            articles.add(new SeedArticle(id, title, series, path.replace('\\', '/')));
        }
        if (articles.isEmpty()) {
            throw new IllegalStateException("课件索引没有可用条目：" + index);
        }
        return articles;
    }

    record SeedArticle(long id, String title, String series, String path) {
    }
}
