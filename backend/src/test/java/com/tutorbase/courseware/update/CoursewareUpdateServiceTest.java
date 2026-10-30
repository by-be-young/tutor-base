package com.tutorbase.courseware.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoursewareUpdateServiceTest {

    @Test
    void diffShowsOnlyChangedLines() {
        String local = "a\nb\nc\n";
        String source = "a\nB\nc\n";

        String diff = CoursewareUpdateService.diff(local, source);

        assertThat(diff).contains("--- local").contains("+++ source");
        assertThat(diff).contains("-b").contains("+B");
        assertThat(diff).doesNotContain("-a");
    }

    @Test
    void diffIsEmptyWhenContentMatches() {
        assertThat(CoursewareUpdateService.diff("x\ny\n", "x\ny\n")).isEmpty();
    }

    @Test
    void diffHandlesMissingLocalDraft() {
        assertThat(CoursewareUpdateService.diff(null, "x\n")).contains("本地无草稿");
        assertThat(CoursewareUpdateService.diff("x\n", null)).isEmpty();
    }

    @Test
    void diffIsTruncatedForHugeChanges() {
        String local = "old\n".repeat(600);
        String source = "new\n".repeat(600);

        String diff = CoursewareUpdateService.diff(local, source);

        assertThat(diff).contains("diff 已截断");
    }
}
