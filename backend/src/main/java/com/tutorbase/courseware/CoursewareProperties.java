package com.tutorbase.courseware;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 课件托管配置。
 *
 * <p>正文与图片只从数据库（正文 blob + 图片行）读取，仓库里不再保留静态课件，
 * 因此没有「静态兜底」开关。
 *
 * @param root                正文 blob 与 manifest 的落盘根目录（本地实现；生产可换成对象存储实现）
 * @param maxArticleBytes     单篇正文大小上限
 * @param legacyClaimsEnabled 旧「认领」写入是否可用（false → 410）
 * @param quotaBytes          每个老师的正文存储配额（字节，0 表示不限）
 * @param maxMediaBytes       单张课件图片大小上限
 * @param mediaSupabaseUrl    图片桶所在 Supabase 项目 URL
 * @param mediaSupabaseAnonKey 公开 anon key（登录与上传请求的 apikey）
 * @param mediaSupabaseEmail  仅具备该桶 INSERT 权限的上传账号
 * @param mediaSupabasePassword 上传账号密码
 * @param mediaBucket         图片桶名
 */
@ConfigurationProperties("tutor.courseware")
public record CoursewareProperties(
        Path root,
        @DefaultValue("1048576") long maxArticleBytes,
        @DefaultValue("true") boolean legacyClaimsEnabled,
        @DefaultValue("2147483648") long quotaBytes,
        @DefaultValue("10485760") long maxMediaBytes,
        @DefaultValue("") String mediaSupabaseUrl,
        @DefaultValue("") String mediaSupabaseAnonKey,
        @DefaultValue("") String mediaSupabaseEmail,
        @DefaultValue("") String mediaSupabasePassword,
        @DefaultValue("courseware-media") String mediaBucket) {

    public CoursewareProperties {
        if (root == null) {
            root = Path.of("var", "courseware");
        }
    }
}
