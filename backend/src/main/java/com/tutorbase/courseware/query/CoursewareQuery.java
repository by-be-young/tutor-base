package com.tutorbase.courseware.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.identity.AccountPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学员侧读取：按老师维度取清单与正文。
 *
 * <p>授权口径（P0，兼容存量全局 ID）：
 * <ul>
 *   <li>学员必须与老师在 {@code student_teacher} 中有关联；</li>
 *   <li>课件可见性沿用 {@code student.permissions}，命中 {@code course_article.legacy_article_id}
 *       （迁移后新旧 ID 相同）或 {@code article_id}；特殊测试学员放开全部；</li>
 *   <li>老师本人（或超级管理员）可读自己命名空间的正文。</li>
 * </ul>
 */
@Service
public class CoursewareQuery {

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewareProperties properties;

    CoursewareQuery(JdbcClient jdbc, CoursewareStorage storage, CoursewareProperties properties) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
    }

    /** 学员可见的全部清单（老师 → 科目），无可见课件时省略该科目。 */
    @Transactional(readOnly = true)
    public List<Manifest> manifestsFor(long studentId) {
        if (servesStatic()) {
            // 静态模式：仍未托管正文，返回空清单让前端继续使用静态目录
            return List.of();
        }
        boolean special = isSpecialTest(studentId);
        int[] permissions = permissions(studentId);

        List<Head> heads = jdbc.sql("""
                SELECT h.teacher_account_id, h.subject, h.release_id, r.version, r.published_at, r.manifest_hash,
                       a.username AS teacher_name
                FROM public.student_teacher AS t
                JOIN public.course_release_head AS h ON h.teacher_account_id = t.teacher_account_id
                JOIN public.course_release AS r ON r.id = h.release_id AND r.status = 'published'
                JOIN public.account AS a ON a.id = h.teacher_account_id
                WHERE t.student_id = :studentId
                ORDER BY a.username, h.subject
                """)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new Head(
                        rs.getLong("teacher_account_id"),
                        rs.getLong("release_id"),
                        rs.getString("subject"),
                        rs.getString("teacher_name"),
                        rs.getInt("version"),
                        rs.getObject("published_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getString("manifest_hash")))
                .list();

        List<Manifest> manifests = new ArrayList<>();
        for (Head head : heads) {
            List<Article> articles = jdbc.sql("""
                    SELECT article_id, legacy_article_id, title, series, logical_path, content_hash, question_count
                    FROM public.course_article
                    WHERE release_id = :releaseId
                    ORDER BY logical_path
                    """)
                    .param("releaseId", head.releaseId())
                    .query((rs, rowNum) -> new Article(
                            rs.getLong("article_id"),
                            rs.getObject("legacy_article_id", Long.class),
                            rs.getString("title"),
                            rs.getString("series"),
                            rs.getString("logical_path"),
                            rs.getString("content_hash"),
                            rs.getInt("question_count")))
                    .list()
                    .stream()
                    .filter(article -> special || allowed(permissions, article))
                    .toList();
            if (!articles.isEmpty()) {
                manifests.add(new Manifest(head.teacherId(), head.teacherName(), head.subject(),
                        head.version(), head.publishedAt(), head.manifestHash(), articles));
            }
        }
        return manifests;
    }

    /** 取正文；鉴权失败抛 {@link CoursewareFailures.Forbidden}，不存在抛 {@link CoursewareFailures.NotFound}。 */
    @Transactional(readOnly = true)
    public Content content(AccountPrincipal principal, long teacherId, long articleId) {
        if (servesStatic()) {
            throw new CoursewareFailures.NotFound("课件托管未启用，请使用静态目录");
        }
        ArticleRow row = jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new ArticleRow(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("content_hash")))
                .optional()
                .orElseThrow(() -> new CoursewareFailures.NotFound("课件不存在：" + teacherId + "/" + articleId));

        authorise(principal, teacherId, row);

        byte[] markdown = storage.getBlob(teacherId, row.contentHash());
        if (markdown == null) {
            throw new CoursewareFailures.NotFound("课件正文缺失：" + teacherId + "/" + articleId);
        }
        return new Content(row.articleId(), row.contentHash(), new String(markdown, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void authorise(AccountPrincipal principal, long teacherId, ArticleRow row) {
        if (principal == null) {
            throw new CoursewareFailures.Forbidden("未登录");
        }
        boolean isAdministrator = "administrator".equals(principal.role());
        if (isAdministrator) {
            if (principal.superAdmin() || principal.accountId() == teacherId) {
                return;
            }
            throw new CoursewareFailures.Forbidden("只能读取自己命名空间内的课件");
        }

        Long learnerId = principal.learnerId();
        if (learnerId == null) {
            throw new CoursewareFailures.Forbidden("当前账号不是学习者");
        }
        boolean linked = jdbc.sql("""
                SELECT 1 FROM public.student_teacher
                WHERE student_id = :studentId AND teacher_account_id = :teacherId
                """)
                .param("studentId", learnerId)
                .param("teacherId", teacherId)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (!linked) {
            throw new CoursewareFailures.Forbidden("未关联该老师");
        }
        if (isSpecialTest(learnerId)) {
            return;
        }
        if (!allowed(permissions(learnerId), new Article(row.articleId(), row.legacyArticleId(), null, null, null, null, 0))) {
            throw new CoursewareFailures.Forbidden("未被授权该课件");
        }
    }

    private boolean servesStatic() {
        return !"storage".equals(properties.source());
    }

    private boolean allowed(int[] permissions, Article article) {        long effective = article.legacyArticleId() != null ? article.legacyArticleId() : article.articleId();
        for (int permission : permissions) {
            if (permission == effective) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpecialTest(long studentId) {
        return jdbc.sql("SELECT is_special_test FROM public.student WHERE id = :id")
                .param("id", studentId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    private int[] permissions(long studentId) {
        List<Integer> values = jdbc.sql("""
                SELECT unnest(permissions) FROM public.student WHERE id = :id
                """)
                .param("id", studentId)
                .query(Integer.class)
                .list();
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private record Head(long teacherId, long releaseId, String subject, String teacherName,
            int version, Instant publishedAt, String manifestHash) {
    }

    private record ArticleRow(long articleId, Long legacyArticleId, String contentHash) {
    }

    /** 清单里的一篇课件。 */
    public record Article(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            int questionCount) {
    }

    /** 一位老师的一个科目清单。 */
    public record Manifest(
            long teacherId,
            String teacherName,
            String subject,
            int version,
            Instant publishedAt,
            String contentHash,
            List<Article> articles) {
    }

    /** 正文响应。 */
    public record Content(long articleId, String contentHash, String markdown) {
    }
}
