package com.tutorbase.courseware.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoursewareSeederTest {

    @TempDir
    Path temp;

    @Test
    void readsIndexAndSkipsIncompleteEntries() throws Exception {
        Path index = temp.resolve("articles.json");
        Files.writeString(index, """
                [
                  {"id": 1, "title": "前导课", "series": "高中化学", "path": "高中化学/理论课讲义/前导课.md"},
                  {"id": 3, "title": "物质的量", "series": "高中化学", "path": "高中化学\\\\理论课讲义\\\\物质的量.md"},
                  {"id": 0, "title": "无 ID", "series": "高中化学", "path": "a.md"},
                  {"id": 9, "title": "缺路径", "series": "高中化学"}
                ]
                """, StandardCharsets.UTF_8);

        List<CoursewareSeeder.SeedArticle> articles =
                CoursewareSeeder.readIndex(index, new ObjectMapper());

        assertThat(articles).hasSize(2);
        assertThat(articles.get(1).id()).isEqualTo(3);
        assertThat(articles.get(1).path()).isEqualTo("高中化学/理论课讲义/物质的量.md");
    }

    @Test
    void rejectsEmptyIndex() throws Exception {
        Path index = temp.resolve("empty.json");
        Files.writeString(index, "[]", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CoursewareSeeder.readIndex(index, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有可用条目");
    }

    @Test
    void answerIdsFollowDocumentOrderIncludingAutoNumbering() {
        String markdown = """
                【题干1】题一
                【@1】
                【题干2】题二
                【@】
                【题干3】题三
                【@3】
                """;

        assertThat(CoursewareValidator.answerIds(markdown)).containsExactly("1", "2", "3");
    }
}
