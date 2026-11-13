package com.tutorbase.courseware.workspace;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareTitles;
import com.tutorbase.courseware.layout.CoursewareLayoutService;
import com.tutorbase.courseware.publish.CoursewareArticleInput;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.publish.CoursewareValidator;
import com.tutorbase.courseware.storage.CoursewareStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Git 工作区：老师用本地 Git 编辑课件，仓库里只有 Markdown，正文与版本仍由服务端托管。
 *
 * <p>{@code manifest} 只给路径与 hash（不含正文），CLI 据 hash 判断哪些文件需要 pull/push；
 * {@code push} 以 {@code baseVersion} 做乐观锁，一次推送就是一个新 release ——
 * 与在线编辑器走同一条发布路径，因此校验、manifest 与审计口径完全一致。
 */
@Service
public class CoursewareWorkspaceService {

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final CoursewarePublisher publisher;
    private final CoursewareLayoutService layouts;

    CoursewareWorkspaceService(JdbcClient jdbc, CoursewareStorage storage, CoursewarePublisher publisher,
            CoursewareLayoutService layouts) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.publisher = publisher;
        this.layouts = layouts;
    }

    /** 工作区清单：当前版本每篇的路径与 hash（不含正文）。 */
    @Transactional(readOnly = true)
    public Manifest manifest(long teacherId, String subject) {
        SubjectHead head = head(teacherId, subject);
        if (head == null) {
            throw new CoursewareFailures.NotFound("该科目还没有发布版本：" + subject);
        }
        return new Manifest(teacherId, subject, head.version(), head.contentHash(), entries(head.releaseId()));
    }

    /** 拉取正文：{@code logicalPath → markdown}，供 CLI 落盘。 */
    @Transactional(readOnly = true)
    public Map<String, String> pull(long teacherId, String subject) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Entry entry : manifest(teacherId, subject).entries()) {
            byte[] markdown = storage.getBlob(teacherId, entry.contentHash());
            if (markdown != null) {
                files.put(entry.logicalPath(), new String(markdown, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    /**
     * 推送变更：{@code files} 覆盖同路径、{@code deletedPaths} 从版本里移除，其余保持现状。
     *
     * @param baseVersion 客户端看到的版本；与当前不一致返回 409
     */
    @Transactional
    public PushResult push(long teacherId, String subject, Integer baseVersion,
            List<WorkspaceFile> files, List<String> deletedPaths) {
        SubjectHead head = head(teacherId, subject);
        if (head == null) {
            throw new CoursewareFailures.NotFound("该科目还没有发布版本：" + subject);
        }
        if (baseVersion == null) {
            throw new CoursewareFailures.Validation(List.of("baseVersion 必填（先 pull 再 push）"));
        }
        if (baseVersion != head.version()) {
            throw new CoursewareFailures.VersionConflict(
                    "本地版本 " + baseVersion + " 已过期，当前版本 " + head.version());
        }
        if ((files == null || files.isEmpty()) && (deletedPaths == null || deletedPaths.isEmpty())) {
            throw new CoursewareFailures.Validation(List.of("没有需要推送的变更"));
        }

        List<Entry> current = entries(head.releaseId());
        Map<String, WorkspaceFile> byPath = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (WorkspaceFile file : files == null ? List.<WorkspaceFile>of() : files) {
            String path = CoursewareValidator.normalizePath(file.logicalPath());
            if (path.isBlank() || !path.endsWith(".md")) {
                errors.add("路径必须是 .md：" + file.logicalPath());
                continue;
            }
            if (file.markdown() == null || file.markdown().isBlank()) {
                errors.add("正文为空：" + path);
                continue;
            }
            if (byPath.put(path, new WorkspaceFile(path, file.markdown())) != null) {
                errors.add("同一路径出现多次：" + path);
            }
            for (String error : layouts.validate(file.markdown())) {
                errors.add(path + " → " + error);
            }
        }
        if (!errors.isEmpty()) {
            throw new CoursewareFailures.Validation(errors);
        }

        List<String> removed = new ArrayList<>();
        for (String rawPath : deletedPaths == null ? List.<String>of() : deletedPaths) {
            String path = CoursewareValidator.normalizePath(rawPath);
            if (current.stream().noneMatch(entry -> entry.logicalPath().equals(path))) {
                throw new CoursewareFailures.Validation(List.of("要删除的路径不在当前版本里：" + path));
            }
            removed.add(path);
        }

        List<CoursewareArticleInput> articles = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (Entry entry : current) {
            if (removed.contains(entry.logicalPath())) {
                continue;
            }
            WorkspaceFile change = byPath.get(entry.logicalPath());
            String markdown = change != null ? change.markdown() : publishedMarkdown(teacherId, entry.contentHash());
            if (markdown == null) {
                continue;
            }
            if (change != null) {
                changed.add(entry.logicalPath());
            }
            articles.add(new CoursewareArticleInput(entry.articleId(), entry.legacyArticleId(), entry.title(),
                    entry.series(), entry.logicalPath(), markdown, answersFor(entry.articleId(), markdown)));
        }
        for (WorkspaceFile change : byPath.values()) {
            if (current.stream().anyMatch(entry -> entry.logicalPath().equals(change.logicalPath()))) {
                continue;
            }
            changed.add(change.logicalPath());
            long articleId = nextArticleId(teacherId);
            articles.add(new CoursewareArticleInput(articleId, null, titleOf(change.logicalPath()), subject,
                    change.logicalPath(), change.markdown(), answersFor(null, change.markdown())));
        }
        if (articles.isEmpty()) {
            throw new CoursewareFailures.Validation(List.of("推送后该科目没有任何课件"));
        }

        CoursewarePublisher.Published published = publisher.publish(teacherId, subject, baseVersion, articles);
        log(teacherId, "{\"subject\":\"" + escape(subject) + "\",\"changed\":" + changed.size()
                + ",\"deleted\":" + removed.size() + ",\"version\":" + published.version() + "}");
        return new PushResult(published.version(), articles.size(), changed.size(), removed.size(),
                changed, removed);
    }

    /**
     * 移动一篇课件到新的逻辑路径（重命名目录/文件名）。
     * 保持 articleId、正文 blob、答案不变，只改 logical_path，并发布为新版本。
     * 全部走 carry-over（不读 blob），避免容器重建后本地 blob 丢失导致发布清空。
     */
    @Transactional
    public PublishedMove moveArticle(long teacherId, long articleId, String newLogicalPath) {
        String subject = findSubject(teacherId, articleId);
        SubjectHead head = head(teacherId, subject);
        if (head == null) {
            throw new CoursewareFailures.NotFound("该科目还没有发布版本：" + subject);
        }
        String normalizedNew = CoursewareValidator.normalizePath(newLogicalPath);
        if (!normalizedNew.endsWith(".md")) {
            throw new CoursewareFailures.Validation(List.of("新路径必须以 .md 结尾：" + newLogicalPath));
        }

        List<Entry> current = entries(head.releaseId());
        Entry target = null;
        for (Entry e : current) {
            if (e.articleId() == articleId) {
                target = e;
                break;
            }
        }
        if (target == null) {
            throw new CoursewareFailures.NotFound("课件不存在或不属于你：" + articleId);
        }
        if (target.logicalPath().equals(normalizedNew)) {
            // 路径没变，无需发布，直接返回当前版本
            return new PublishedMove(subject, head.version(), normalizedNew, normalizedNew);
        }
        for (Entry e : current) {
            if (e.logicalPath().equals(normalizedNew)) {
                throw new CoursewareFailures.Validation(List.of("目标路径已被占用：" + normalizedNew));
            }
        }

        List<CoursewareArticleInput> articles = new ArrayList<>();
        String newTitle = CoursewareTitles.fromLogicalPath(normalizedNew);
        for (Entry e : current) {
            String path = e.articleId() == articleId ? normalizedNew : e.logicalPath();
            String title = e.articleId() == articleId ? newTitle : e.title();
            if (e.articleId() == articleId) {
                // 正文里的首标题与目录名是两处显示：能读到 blob 时一并改掉，读不到就继续结转。
                String markdown = publishedMarkdown(teacherId, e.contentHash());
                String retitled = markdown == null ? null : CoursewareTitles.retitle(markdown, newTitle);
                if (retitled != null && !retitled.equals(markdown)) {
                    articles.add(new CoursewareArticleInput(
                            e.articleId(), e.legacyArticleId(), title, e.series(),
                            path, retitled, answersFor(e.articleId(), retitled)));
                    continue;
                }
            }
            articles.add(CoursewareArticleInput.carried(
                    e.articleId(), e.legacyArticleId(), title, e.series(),
                    path, e.contentHash(), e.answerHash(), e.questionCount()));
        }

        CoursewarePublisher.Published published = publisher.publish(teacherId, subject, head.version(), articles);
        return new PublishedMove(subject, published.version(), target.logicalPath(), normalizedNew);
    }

    private String findSubject(long teacherId, long articleId) {
        return jdbc.sql("""
                SELECT DISTINCT ca.subject
                FROM public.course_article ca
                JOIN public.course_release_head h ON h.release_id = ca.release_id
                WHERE ca.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new CoursewareFailures.NotFound("课件不存在或不属于你：" + articleId));
    }

    private List<Entry> entries(long releaseId) {
        return jdbc.sql("""
                SELECT article_id, legacy_article_id, title, series, logical_path, content_hash, answer_hash, question_count
                FROM public.course_article
                WHERE release_id = :releaseId
                ORDER BY logical_path
                """)
                .param("releaseId", releaseId)
                .query((rs, rowNum) -> new Entry(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getInt("question_count")))
                .list();
    }

    private SubjectHead head(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT h.release_id, r.version, r.manifest_hash
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new SubjectHead(
                        rs.getLong("release_id"), rs.getInt("version"), rs.getString("manifest_hash")))
                .optional()
                .orElse(null);
    }

    private String publishedMarkdown(long teacherId, String contentHash) {
        byte[] bytes = storage.getBlob(teacherId, contentHash);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private List<CoursewareArticleInput.CoursewareAnswerInput> answersFor(Long articleId, String markdown) {
        Map<String, CoursewareArticleInput.CoursewareAnswerInput> stored = new LinkedHashMap<>();
        if (articleId != null) {
            jdbc.sql("""
                    SELECT question_id, answer_text, auto_grade
                    FROM public.article_answer_keys
                    WHERE blog_id = :blogId
                    """)
                    .param("blogId", articleId)
                    .query((rs, rowNum) -> new CoursewareArticleInput.CoursewareAnswerInput(
                            rs.getString("question_id"), rs.getString("answer_text"), rs.getBoolean("auto_grade")))
                    .list()
                    .forEach(answer -> stored.put(answer.questionId(), answer));
        }
        List<CoursewareArticleInput.CoursewareAnswerInput> answers = new ArrayList<>();
        for (String id : CoursewareValidator.answerIds(markdown)) {
            answers.add(stored.getOrDefault(id,
                    new CoursewareArticleInput.CoursewareAnswerInput(id, "", false)));
        }
        return answers;
    }

    private long nextArticleId(long teacherId) {
        Long max = jdbc.sql("""
                SELECT max(id) FROM (
                    SELECT article_id AS id FROM public.course_article WHERE teacher_account_id = :teacherId
                    UNION ALL
                    SELECT article_id FROM public.course_draft WHERE teacher_account_id = :teacherId
                    UNION ALL
                    SELECT local_article_id FROM public.course_copy WHERE owner_account_id = :teacherId
                ) AS existing
                """)
                .param("teacherId", teacherId)
                .query(Long.class)
                .optional()
                .orElse(null);
        return max == null ? 1000L : Math.max(max + 1, 1000L);
    }

    private static String titleOf(String logicalPath) {
        int slash = logicalPath.lastIndexOf('/');
        String name = slash < 0 ? logicalPath : logicalPath.substring(slash + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private void log(long teacherId, String detailJson) {
        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, detail)
                VALUES (:teacherId, 'git_push', :teacherId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherId)
                .param("detail", detailJson)
                .update();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SubjectHead(long releaseId, int version, String contentHash) {
    }

    /** 清单里的一篇。 */
    public record Entry(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            String answerHash,
            int questionCount) {
    }

    /** 工作区清单。 */
    public record Manifest(
            long teacherId,
            String subject,
            int version,
            String contentHash,
            List<Entry> entries) {
    }

    /** 一份待推送的正文。 */
    public record WorkspaceFile(String logicalPath, String markdown) {
    }

    /** 推送结果。 */
    public record PushResult(
            int version,
            int articleCount,
            int changedCount,
            int deletedCount,
            List<String> changedPaths,
            List<String> deletedPaths) {
    }

    /** 移动结果。 */
    public record PublishedMove(String subject, int version, String oldPath, String newPath) {
    }
}
