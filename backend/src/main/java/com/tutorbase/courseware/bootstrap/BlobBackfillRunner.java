package com.tutorbase.courseware.bootstrap;

import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动时补写缺失的正文 blob。
 *
 * <p>历史上部分 release 是通过直连数据库创建的（sync 脚本），只写了 content_hash
 * 却没有把正文写到 LocalFileStorage。切到 storage 模式前，需要把这些 blob 补回去，
 * 否则学员端读正文会 500。正文来源是打包在 JAR 里的 courseware-static/。
 */
@Component
public class BlobBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobBackfillRunner.class);

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;

    public BlobBackfillRunner(JdbcClient jdbc, CoursewareStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BlobRef> refs = jdbc.sql("""
                SELECT ca.teacher_account_id, ca.article_id, ca.logical_path, ca.content_hash
                FROM public.course_article ca
                JOIN public.course_release_head h ON h.release_id = ca.release_id
                WHERE ca.content_hash IS NOT NULL
                """)
                .query((rs, rowNum) -> new BlobRef(
                        rs.getLong("teacher_account_id"),
                        rs.getLong("article_id"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash")))
                .list();

        int filled = 0;
        int skipped = 0;
        int missingSource = 0;
        for (BlobRef ref : refs) {
            if (storage.getBlob(ref.teacherId(), ref.contentHash()) != null) {
                skipped++;
                continue;
            }

            String resourcePath = "/courseware-static/" + ref.logicalPath();
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                missingSource++;
                LOGGER.warn("Blob 缺失且静态资源不存在: articleId={}, path={}, hash={}",
                        ref.articleId(), ref.logicalPath(), ref.contentHash());
                continue;
            }

            try {
                byte[] content = resource.getInputStream().readAllBytes();
                String actualHash = LocalFileStorage.sha256(content);
                if (!actualHash.equals(ref.contentHash())) {
                    LOGGER.warn("哈希不匹配: articleId={}, path={}, dbHash={}, actualHash={}",
                            ref.articleId(), ref.logicalPath(), ref.contentHash(), actualHash);
                    continue;
                }
                storage.putBlob(ref.teacherId(), content);
                filled++;
            } catch (Exception e) {
                LOGGER.warn("补写 blob 失败: articleId={}, path={}: {}",
                        ref.articleId(), ref.logicalPath(), e.getMessage());
            }
        }

        LOGGER.info("Blob 补写完成: 新补={}, 已存在={}, 静态资源缺失={}, 总计={}",
                filled, skipped, missingSource, refs.size());
    }

    private record BlobRef(long teacherId, long articleId, String logicalPath, String contentHash) {
    }
}
