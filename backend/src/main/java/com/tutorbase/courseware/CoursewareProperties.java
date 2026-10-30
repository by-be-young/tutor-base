package com.tutorbase.courseware;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 课件托管配置。
 *
 * @param root            正文 blob 与 manifest 的落盘根目录（本地实现；生产可换成对象存储实现）
 * @param source          课件来源：storage = 走后端发布与鉴权；static = 回落到 public/articles（仅开发/兜底）
 * @param maxArticleBytes 单篇正文大小上限
 */
@ConfigurationProperties("tutor.courseware")
public record CoursewareProperties(
        Path root,
        @DefaultValue("storage") String source,
        @DefaultValue("1048576") long maxArticleBytes) {

    public CoursewareProperties {
        if (root == null) {
            root = Path.of("var", "courseware");
        }
        if (!"storage".equals(source) && !"static".equals(source)) {
            throw new IllegalArgumentException("tutor.courseware.source 只能是 storage 或 static");
        }
    }
}
