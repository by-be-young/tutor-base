package com.tutorbase.courseware.publish;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoursewareValidatorTest {

    private static final long MAX = 1_048_576;

    private static CoursewareArticleInput article(String markdown, List<CoursewareArticleInput.CoursewareAnswerInput> answers) {
        return new CoursewareArticleInput(null, null, "3-1 原电池", "高中化学",
                "高中化学/理论课讲义/3-1 原电池.md", markdown, answers);
    }

    private static List<CoursewareArticleInput.CoursewareAnswerInput> answers(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new CoursewareArticleInput.CoursewareAnswerInput(id, "答案" + id, true))
                .toList();
    }

    private static final String VALID = """
            # 原电池

            【题干1】下列说法正确的是（ ）

            A. 选项一

            【@1】

            【题干2】下列说法错误的是（ ）

            A. 选项二

            【@2】
            """;

    @Test
    void acceptsWellFormedArticle() {
        assertThat(CoursewareValidator.validate(article(VALID, answers("1", "2")), MAX)).isEmpty();
    }

    @Test
    void acceptsSharedStemRange() {
        String markdown = """
                【题干1-2】阅读材料：

                【题干1】（1）第一问 ______。

                【@1】

                【题干2】（2）第二问 ______。

                【@2】
                """;
        assertThat(CoursewareValidator.validate(article(markdown, answers("1", "2")), MAX)).isEmpty();
    }

    @Test
    void rejectsMissingStemMarker() {
        String markdown = "【题干1】有题干\n\n【@1】\n\n【@2】\n";
        assertThat(CoursewareValidator.validate(article(markdown, answers("1", "2")), MAX))
                .anySatisfy(error -> assertThat(error).contains("第 2 题缺少题干标记"));
    }

    @Test
    void rejectsDuplicateAnswerPlaceholder() {
        String markdown = "【题干1】题\n\n【@1】\n\n【@1】\n";
        assertThat(CoursewareValidator.validate(article(markdown, answers("1")), MAX))
                .anySatisfy(error -> assertThat(error).contains("编号重复"));
    }

    @Test
    void rejectsAnswersThatDoNotMatchQuestions() {
        String markdown = "【题干1】题一\n\n【@1】\n\n【题干2】题二\n\n【@2】\n";
        assertThat(CoursewareValidator.validate(article(markdown, answers("1")), MAX))
                .anySatisfy(error -> assertThat(error).contains("答案键数量"));
    }

    @Test
    void rejectsExternalImagesAndScripts() {
        String external = "【题干1】题 ![图](https://example.com/a.png)\n\n【@1】\n";
        assertThat(CoursewareValidator.validate(article(external, answers("1")), MAX))
                .anySatisfy(error -> assertThat(error).contains("外链图片"));

        String script = "【题干1】题 <script>alert(1)</script>\n\n【@1】\n";
        assertThat(CoursewareValidator.validate(article(script, answers("1")), MAX))
                .anySatisfy(error -> assertThat(error).contains("疑似脚本"));
    }

    @Test
    void rejectsUnsafeLogicalPaths() {
        for (String path : List.of("../a.md", "/abs/a.md", "a\\b.md", "a/b.txt", "a//b.md")) {
            CoursewareArticleInput input = new CoursewareArticleInput(null, null, "标题", "高中化学",
                    path, VALID, answers("1", "2"));
            assertThat(CoursewareValidator.validate(input, MAX))
                    .as("path=%s", path)
                    .isNotEmpty();
        }
    }

    @Test
    void acceptsLocalImageReferences() {
        String markdown = "【题干1】如图 ![[化学-课后-1-1-1-2.png]]\n\n【@1】\n";
        assertThat(CoursewareValidator.validate(article(markdown, answers("1")), MAX)).isEmpty();
    }

    @Test
    void rejectsOversizedArticle() {
        String markdown = "【题干1】" + "字".repeat(50) + "\n\n【@1】\n";
        assertThat(CoursewareValidator.validate(article(markdown, answers("1")), 40))
                .anySatisfy(error -> assertThat(error).contains("字节上限"));
    }
}
