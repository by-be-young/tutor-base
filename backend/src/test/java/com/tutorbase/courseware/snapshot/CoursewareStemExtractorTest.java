package com.tutorbase.courseware.snapshot;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoursewareStemExtractorTest {

    private static final String SAMPLE = """
            # 一、原电池

            【题干1】下列说法正确的是（ ）

            A. 选项一

            B. 选项二

            【@1】

            【题干2-3】阅读材料：某电池的工作原理如图。

            【题干2】（1）写出正极反应式 ______。

            【@2】

            【题干3】（2）计算转移电子数 ______。

            【@3】
            """;

    @Test
    void extractsStemUpToAnswerMarker() {
        assertThat(CoursewareStemExtractor.extract(SAMPLE, "1"))
                .isEqualTo("下列说法正确的是（ ）\nA. 选项一\nB. 选项二");
    }

    @Test
    void sharedStemRangeAppliesToEveryQuestionInRange() {
        String stem = CoursewareStemExtractor.extract(SAMPLE, "2");
        assertThat(stem).contains("（1）写出正极反应式");
    }

    @Test
    void stopsAtSharedStemFollowingQuestion() {
        // 题干3 自己的内容不包含题干 2 的文本
        String stem3 = CoursewareStemExtractor.extract(SAMPLE, "3");
        assertThat(stem3).contains("（2）计算转移电子数");
        assertThat(stem3).doesNotContain("正极反应式");
    }

    @Test
    void stripsQuotePrefixesHeadingsAndBold() {
        String markdown = """
                > 【题干5】**加粗的重点**

                > 正文第一行

                > # 标题不该出现

                【@5】
                """;
        String stem = CoursewareStemExtractor.extract(markdown, "5");
        assertThat(stem).isEqualTo("加粗的重点\n正文第一行");
    }

    @Test
    void stopsAtSeparatorLine() {
        String markdown = """
                【题干1】题干内容

                ---

                1. 作答区里的标签

                【@1】
                """;
        assertThat(CoursewareStemExtractor.extract(markdown, "1")).isEqualTo("题干内容");
    }

    @Test
    void returnsEmptyWhenMarkerMissing() {
        assertThat(CoursewareStemExtractor.extract("【题干1】题\n\n【@1】\n", "2")).isEmpty();
        assertThat(CoursewareStemExtractor.extract(null, "1")).isEmpty();
        assertThat(CoursewareStemExtractor.extract("正文", "")).isEmpty();
    }

    @Test
    void truncatesLongStem() {
        String longLine = "字".repeat(600);
        String markdown = "【题干1】" + longLine + "\n\n【@1】\n";
        String stem = CoursewareStemExtractor.extract(markdown, "1");
        assertThat(stem.length()).isLessThanOrEqualTo(401);
        assertThat(stem).endsWith("…");
    }

    @Test
    void questionIdsFollowDocumentOrder() {
        assertThat(CoursewareStemExtractor.questionIds("【题干1】a\n【@1】\n【@】\n【@3】\n"))
                .containsExactly("1", "2", "3");
        assertThat(List.of()).isEmpty();
    }
}
