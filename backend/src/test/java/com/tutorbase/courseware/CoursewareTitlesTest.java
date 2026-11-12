package com.tutorbase.courseware;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoursewareTitlesTest {

    @Test
    void retitleUpdatesFrontMatterAndFirstHeading() {
        String markdown = """
                ---
                title: 虚拟语气
                defaultLayout: A
                ---

                # 虚拟语气

                正文
                """;

        String updated = CoursewareTitles.retitle(markdown, "3 虚拟语气");

        assertThat(updated).contains("title: 3 虚拟语气")
                .contains("# 3 虚拟语气")
                .contains("正文");
    }

    @Test
    void retitleLeavesAContentHeadingThatIsNotTheTitleAlone() {
        String markdown = "# 一、语气的本质\n\n正文";
        assertThat(CoursewareTitles.retitle(markdown, "虚拟语气")).isEqualTo(markdown);
    }

    @Test
    void titleOfPrefersFrontMatterThenHeading() {
        assertThat(CoursewareTitles.titleOf("---\ntitle: 口语训练\ndefaultLayout: A\n---\n\n# 别的标题"))
                .isEqualTo("口语训练");
        assertThat(CoursewareTitles.titleOf("# 精读任务-1\n\n正文")).isEqualTo("精读任务-1");
        assertThat(CoursewareTitles.titleOf("没有标题")).isEmpty();
    }

    @Test
    void fromLogicalPathDropsDirectoryAndSuffix() {
        assertThat(CoursewareTitles.fromLogicalPath("高中英语/语法课讲义/3 虚拟语气.md"))
                .isEqualTo("3 虚拟语气");
    }
}
