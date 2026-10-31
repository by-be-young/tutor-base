package com.tutorbase.courseware.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoursewareLayoutServiceTest {

    @Test
    void layoutIdsComeFromDirectivesAndFrontMatter() {
        String markdown = """
                ---
                title: 练习3
                defaultLayout: B
                ---

                <!-- @layout:A -->
                # 一、知识点

                - 要点

                <!-- @layout:C -->
                # 二、对比

                | 项目 | A |
                | --- | --- |
                """;

        assertThat(CoursewareLayoutService.layoutIdsOf(markdown)).containsExactly("A", "C", "B");
        assertThat(CoursewareLayoutService.layoutIdsOf("【题干1】题\n\n【@1】\n")).isEmpty();
    }

    @Test
    void detectsEmptySections() {
        String markdown = """
                <!-- @layout:A -->
                # 有内容的节

                正文

                # 空节

                # 又一个空节
                """;

        assertThat(CoursewareLayoutService.emptySections(markdown))
                .containsExactly("section 为空：空节", "section 为空：又一个空节");
    }

    @Test
    void skeletonFollowsLayout() {
        String practice = CoursewareLayoutService.skeleton("练习3", "B");
        assertThat(practice).contains("defaultLayout: B").contains("<!-- @layout:B -->")
                .contains("【题干1】").contains("【@1】");

        String lecture = CoursewareLayoutService.skeleton("讲义1", "A");
        assertThat(lecture).contains("<!-- @layout:A -->").doesNotContain("【@1】");

        assertThat(CoursewareLayoutService.skeleton(null, null)).contains("title: 新课件")
                .contains("defaultLayout: A");
    }

    @Test
    void plainMarkdownPassesValidation() {
        assertThat(CoursewareLayoutService.emptySections("# 标题\n\n正文\n")).isEmpty();
        assertThat(CoursewareLayoutService.layoutIdsOf(null)).isEmpty();
        assertThat(List.of()).isEmpty();
    }
}
