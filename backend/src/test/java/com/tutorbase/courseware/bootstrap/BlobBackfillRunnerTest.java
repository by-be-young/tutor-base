package com.tutorbase.courseware.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class BlobBackfillRunnerTest {

    @Test
    void locatesStaticArticleByExactPath() {
        Resource resource = BlobBackfillRunner.locateStaticResource(
                "高中英语/写作课讲义/作文练习.md", "作文练习");
        assertThat(resource).isNotNull();
    }

    @Test
    void locatesStaticArticleAfterTheTeacherAddsANumberToTheFileName() throws Exception {
        Resource resource = BlobBackfillRunner.locateStaticResource(
                "高中英语/语法课讲义/3 虚拟语气.md", "3 虚拟语气");
        assertThat(resource).isNotNull();
        assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).startsWith("# 虚拟语气");
    }

    @Test
    void returnsNullWhenNoStaticArticleExists() {
        assertThat(BlobBackfillRunner.locateStaticResource("高中英语/不存在/没有.md", "没有")).isNull();
    }
}
