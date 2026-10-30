package com.tutorbase.courseware.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 存量课件必须能通过发布校验：索引里的每一篇都按发布口径校验一遍。
 */
class ExistingCoursewarePublishabilityTest {

    private static final Path WEB_ROOT = Path.of("..", "tutor-nest-vue", "public");

    @Test
    void everyIndexedArticlePassesPublishValidation() throws Exception {
        Path index = WEB_ROOT.resolve("data").resolve("articles.json");
        Path root = WEB_ROOT.resolve("articles");
        assumeTrue(Files.exists(index) && Files.isDirectory(root), "未找到前端课件目录");

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (CoursewareSeeder.SeedArticle article : CoursewareSeeder.readIndex(index, new ObjectMapper())) {
            Path file = root.resolve(article.path());
            String markdown = Files.readString(file, StandardCharsets.UTF_8);
            List<CoursewareArticleInput.CoursewareAnswerInput> answers = CoursewareValidator.answerIds(markdown)
                    .stream()
                    .map(id -> new CoursewareArticleInput.CoursewareAnswerInput(id, "A", true))
                    .toList();
            CoursewareArticleInput input = new CoursewareArticleInput(article.id(), article.id(), article.title(),
                    article.series(), article.path(), markdown, answers);
            for (String error : CoursewareValidator.validate(input, 1_048_576)) {
                problems.add(article.path() + " → " + error);
            }
            checked++;
        }

        assertThat(checked).isGreaterThan(30);
        assertThat(problems).isEmpty();
    }
}
