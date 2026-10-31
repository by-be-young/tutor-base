package com.tutorbase.courseware.snapshot;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.tutorbase.courseware.storage.CoursewareStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把「全局 article_id」解析到当前发布版本，并给出题干快照。
 *
 * <p>作答与错题写入时调用：一次查库拿到 {@code teacherAccountId / releaseId / subject / markdown}，
 * 之后用 {@link CoursewareStemExtractor} 抽出题干写入 {@code stem_snapshot}。
 * 解析不到（课件未托管、旧授权指向已下线篇目）时返回空结果，调用方按原有逻辑继续，
 * 不会因为托管数据缺失而阻塞学员作答。
 */
@Service
public class CoursewareSnapshotResolver {

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;

    CoursewareSnapshotResolver(JdbcClient jdbc, CoursewareStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /** 按全局 article_id（优先 legacy_article_id，回落 article_id）找当前版本里的一篇。 */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolve(long articleId) {
        Optional<Row> row = jdbc.sql("""
                SELECT ca.teacher_account_id, ca.release_id, ca.subject, ca.title, ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE ca.legacy_article_id = :articleId OR ca.article_id = :articleId
                ORDER BY (ca.legacy_article_id = :articleId) DESC
                LIMIT 1
                """)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new Row(
                        rs.getLong("teacher_account_id"),
                        rs.getLong("release_id"),
                        rs.getString("subject"),
                        rs.getString("title"),
                        rs.getString("content_hash")))
                .optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Row resolved = row.get();
        byte[] markdown = storage.getBlob(resolved.teacherAccountId(), resolved.contentHash());
        String text = markdown == null ? null : new String(markdown, StandardCharsets.UTF_8);
        return Optional.of(new Resolved(resolved.teacherAccountId(), resolved.releaseId(),
                resolved.subject(), resolved.title(), text));
    }

    /** 题干快照：解析不到就返回空串（写入 NULL 更省空间，由调用方决定）。 */
    @Transactional(readOnly = true)
    public String stemOf(long articleId, String questionId) {
        return resolve(articleId)
                .map(resolved -> CoursewareStemExtractor.extract(resolved.markdown(), questionId))
                .orElse("");
    }

    private record Row(
            long teacherAccountId, long releaseId, String subject, String title, String contentHash) {
    }

    /** 解析结果。 */
    public record Resolved(
            long teacherAccountId,
            long releaseId,
            String subject,
            String title,
            String markdown) {
    }
}
