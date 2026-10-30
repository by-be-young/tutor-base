package com.tutorbase.courseware.update;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 来源更新：汇总、单篇差异、覆盖或忽略。
 *
 * <p>v1 采用整篇粒度：不做三方合并。覆盖前把本地草稿备份成 blob 并把 hash 返回，
 * 覆盖以 {@code expectedSourceHash} 做乐观锁，避免"看到旧 diff、来源已再次发布"。
 */
@Service
public class CoursewareUpdateService {

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;

    CoursewareUpdateService(JdbcClient jdbc, CoursewareStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /** 更新汇总：有更新的副本、来源已下线的副本、同科目下尚未获取的新增篇目。 */
    @Transactional(readOnly = true)
    public Digest digest(long teacherId) {
        List<CopyRow> copies = copiesOf(teacherId);
        List<DigestItem> updated = new ArrayList<>();
        List<DigestItem> removed = new ArrayList<>();
        List<DigestItem> added = new ArrayList<>();
        for (CopyRow copy : copies) {
            SourceRow source = source(copy.sourceTeacherAccountId(), copy.sourceArticleId());
            if (source == null) {
                removed.add(item(copy, null));
                continue;
            }
            boolean hasUpdate = !source.contentHash().equals(copy.baseContentHash())
                    && !source.contentHash().equals(copy.dismissedHash());
            if (hasUpdate) {
                updated.add(item(copy, source));
            }
        }
        added.addAll(addedInSharedSubjects(teacherId, copies));
        return new Digest(updated.size(), added.size(), removed.size(), updated, added, removed);
    }

    /** 单篇更新详情：hash、本地是否改动、行级 diff 与答案变更标记。 */
    @Transactional(readOnly = true)
    public UpdateDetail detail(long teacherId, long copyId) {
        CopyRow copy = requireCopy(teacherId, copyId);
        SourceRow source = source(copy.sourceTeacherAccountId(), copy.sourceArticleId());
        if (source == null) {
            throw new CoursewareFailures.NotFound("来源课件已下线：" + copy.sourceArticleId());
        }
        String localMarkdown = localMarkdown(teacherId, copy.localArticleId());
        String localHash = localMarkdown == null ? null : LocalFileStorage.sha256(
                localMarkdown.getBytes(StandardCharsets.UTF_8));
        String sourceMarkdown = markdownOf(copy.sourceTeacherAccountId(), source.contentHash());
        boolean localModified = localHash != null && !localHash.equals(copy.baseContentHash());
        boolean answerChanged = !source.answerHash().equals(copy.baseAnswerHash());
        return new UpdateDetail(
                copyId,
                source.version(),
                copy.baseContentHash(),
                source.contentHash(),
                localModified,
                answerChanged,
                diff(localMarkdown, sourceMarkdown),
                source.title(),
                source.logicalPath());
    }

    /**
     * 应用或忽略更新。
     *
     * @param mode {@code overwrite} 覆盖本地草稿，{@code keep} 记住当前来源 hash 不再提示
     */
    @Transactional
    public ApplyResult apply(long teacherId, long copyId, String mode, String expectedSourceHash) {
        CopyRow copy = requireCopy(teacherId, copyId);
        SourceRow source = source(copy.sourceTeacherAccountId(), copy.sourceArticleId());
        if (source == null) {
            throw new CoursewareFailures.NotFound("来源课件已下线：" + copy.sourceArticleId());
        }
        if (expectedSourceHash != null && !expectedSourceHash.isBlank()
                && !expectedSourceHash.equals(source.contentHash())) {
            throw new CoursewareFailures.HashConflict("来源已发布新版本，请重新拉取差异");
        }
        if ("keep".equals(mode)) {
            jdbc.sql("UPDATE public.course_copy SET dismissed_hash = :hash WHERE id = :copyId")
                    .param("hash", source.contentHash())
                    .param("copyId", copyId)
                    .update();
            log(teacherId, copy.localArticleId(), "dismiss_update",
                    "{\"copyId\":" + copyId + ",\"sourceHash\":\"" + source.contentHash() + "\"}");
            return new ApplyResult(false, null, false);
        }
        if (!"overwrite".equals(mode)) {
            throw new CoursewareFailures.Validation(List.of("mode 只能是 overwrite 或 keep"));
        }

        String backupHash = null;
        String localMarkdown = localMarkdown(teacherId, copy.localArticleId());
        if (localMarkdown != null) {
            backupHash = storage.putBlob(teacherId, localMarkdown.getBytes(StandardCharsets.UTF_8));
        }
        String sourceMarkdown = markdownOf(copy.sourceTeacherAccountId(), source.contentHash());
        if (sourceMarkdown == null) {
            throw new CoursewareFailures.NotFound("来源正文缺失：" + source.contentHash());
        }
        storage.putBlob(teacherId, sourceMarkdown.getBytes(StandardCharsets.UTF_8));
        saveDraft(teacherId, copy.localArticleId(), sourceMarkdown);
        jdbc.sql("""
                UPDATE public.course_copy
                SET base_content_hash = :contentHash, base_answer_hash = :answerHash,
                    source_release_id = :releaseId, source_logical_path = :sourcePath,
                    dismissed_hash = NULL, status = 'active'
                WHERE id = :copyId
                """)
                .param("contentHash", source.contentHash())
                .param("answerHash", source.answerHash())
                .param("releaseId", source.releaseId())
                .param("sourcePath", source.logicalPath())
                .param("copyId", copyId)
                .update();
        log(teacherId, copy.localArticleId(), "apply_update",
                "{\"copyId\":" + copyId + ",\"sourceHash\":\"" + source.contentHash() + "\"}");
        return new ApplyResult(true, backupHash, !source.answerHash().equals(copy.baseAnswerHash()));
    }

    private List<DigestItem> addedInSharedSubjects(long teacherId, List<CopyRow> copies) {
        List<DigestItem> added = new ArrayList<>();
        List<String> visited = new ArrayList<>();
        for (CopyRow copy : copies) {
            String subject = subject(copy.sourceTeacherAccountId(), copy.sourceArticleId());
            if (subject == null) {
                continue;
            }
            String key = copy.sourceTeacherAccountId() + ":" + subject;
            if (visited.contains(key)) {
                continue;
            }
            visited.add(key);
            if (!copyAllowed(copy.sourceTeacherAccountId(), subject)) {
                continue;
            }
            for (SourceRow source : publishedArticles(copy.sourceTeacherAccountId(), subject)) {
                boolean known = copies.stream().anyMatch(existing ->
                        existing.sourceTeacherAccountId() == copy.sourceTeacherAccountId()
                                && existing.sourceArticleId() == source.articleId());
                if (!known) {
                    added.add(new DigestItem(null, null, source.articleId(), source.title(),
                            source.logicalPath(), null, null));
                }
            }
        }
        return added;
    }

    private boolean copyAllowed(long sourceTeacherId, String subject) {
        return jdbc.sql("""
                SELECT bool_or(allow_copy) FROM public.course_share
                WHERE owner_teacher_account_id = :teacherId AND subject = :subject AND revoked_at IS NULL
                """)
                .param("teacherId", sourceTeacherId)
                .param("subject", subject)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    private String subject(long teacherId, long articleId) {
        return jdbc.sql("""
                SELECT ca.subject
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private List<SourceRow> publishedArticles(long teacherId, String subject) {
        return jdbc.sql("""
                SELECT ca.article_id, ca.title, ca.logical_path, ca.content_hash, ca.answer_hash,
                       r.id AS release_id, r.version
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND h.subject = :subject
                ORDER BY ca.logical_path
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new SourceRow(
                        rs.getLong("article_id"),
                        rs.getString("title"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getLong("release_id"),
                        rs.getInt("version")))
                .list();
    }

    private SourceRow source(long teacherId, long articleId) {
        return jdbc.sql("""
                SELECT ca.article_id, ca.title, ca.logical_path, ca.content_hash, ca.answer_hash,
                       r.id AS release_id, r.version
                FROM public.course_release_head AS h
                JOIN public.course_release AS r ON r.id = h.release_id
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new SourceRow(
                        rs.getLong("article_id"),
                        rs.getString("title"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash"),
                        rs.getString("answer_hash"),
                        rs.getLong("release_id"),
                        rs.getInt("version")))
                .optional()
                .orElse(null);
    }

    private List<CopyRow> copiesOf(long teacherId) {
        return jdbc.sql("""
                SELECT id, local_article_id, source_teacher_account_id, source_article_id,
                       base_content_hash, base_answer_hash, dismissed_hash, local_title
                FROM public.course_copy
                WHERE owner_account_id = :teacherId AND status = 'active'
                ORDER BY id
                """)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new CopyRow(
                        rs.getLong("id"),
                        rs.getLong("local_article_id"),
                        rs.getLong("source_teacher_account_id"),
                        rs.getLong("source_article_id"),
                        rs.getString("base_content_hash"),
                        rs.getString("base_answer_hash"),
                        rs.getString("dismissed_hash"),
                        rs.getString("local_title")))
                .list();
    }

    private CopyRow requireCopy(long teacherId, long copyId) {
        return jdbc.sql("""
                SELECT id, local_article_id, source_teacher_account_id, source_article_id,
                       base_content_hash, base_answer_hash, dismissed_hash, local_title
                FROM public.course_copy
                WHERE id = :copyId AND owner_account_id = :teacherId
                """)
                .param("copyId", copyId)
                .param("teacherId", teacherId)
                .query((rs, rowNum) -> new CopyRow(
                        rs.getLong("id"),
                        rs.getLong("local_article_id"),
                        rs.getLong("source_teacher_account_id"),
                        rs.getLong("source_article_id"),
                        rs.getString("base_content_hash"),
                        rs.getString("base_answer_hash"),
                        rs.getString("dismissed_hash"),
                        rs.getString("local_title")))
                .optional()
                .orElseThrow(() -> new CoursewareFailures.NotFound("获取记录不存在：" + copyId));
    }

    private String localMarkdown(long teacherId, long articleId) {
        return jdbc.sql("""
                SELECT markdown FROM public.course_draft
                WHERE teacher_account_id = :teacherId AND article_id = :articleId
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String markdownOf(long teacherId, String contentHash) {
        byte[] bytes = storage.getBlob(teacherId, contentHash);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private void saveDraft(long teacherId, long articleId, String markdown) {
        jdbc.sql("""
                INSERT INTO public.course_draft (teacher_account_id, article_id, markdown, updated_at)
                VALUES (:teacherId, :articleId, :markdown, now())
                ON CONFLICT (teacher_account_id, article_id) DO UPDATE
                    SET markdown = EXCLUDED.markdown, updated_at = now()
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .param("markdown", markdown)
                .update();
    }

    private void log(long teacherId, long articleId, String action, String detailJson) {
        jdbc.sql("""
                INSERT INTO public.course_operation_log
                    (actor_account_id, action, teacher_account_id, article_id, detail)
                VALUES (:teacherId, :action, :teacherId, :articleId, cast(:detail as jsonb))
                """)
                .param("teacherId", teacherId)
                .param("articleId", articleId)
                .param("action", action)
                .param("detail", detailJson)
                .update();
    }

    private static DigestItem item(CopyRow copy, SourceRow source) {
        return new DigestItem(copy.id(), copy.localArticleId(),
                source == null ? copy.sourceArticleId() : source.articleId(),
                source == null ? copy.localTitle() : source.title(),
                source == null ? null : source.logicalPath(),
                copy.baseContentHash(),
                source == null ? null : source.contentHash());
    }

    /** 行级 diff（LCS），只输出上下文相关行，避免整篇正文进响应。 */
    static String diff(String local, String source) {
        if (source == null) return "";
        if (local == null) return "@@ 本地无草稿 @@\n";
        List<String> left = List.of(local.split("\n", -1));
        List<String> right = List.of(source.split("\n", -1));
        int[][] lcs = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                lcs[i][j] = left.get(i).equals(right.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        StringBuilder builder = new StringBuilder("--- local\n+++ source\n");
        int i = 0;
        int j = 0;
        int emitted = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).equals(right.get(j))) {
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                builder.append('-').append(left.get(i++)).append('\n');
                emitted++;
            } else {
                builder.append('+').append(right.get(j++)).append('\n');
                emitted++;
            }
            if (emitted >= MAX_DIFF_LINES) {
                builder.append("… diff 已截断\n");
                return builder.toString();
            }
        }
        while (i < left.size()) {
            builder.append('-').append(left.get(i++)).append('\n');
            emitted++;
        }
        while (j < right.size()) {
            builder.append('+').append(right.get(j++)).append('\n');
            emitted++;
        }
        if (emitted == 0) {
            return "";
        }
        return builder.toString();
    }

    private static final int MAX_DIFF_LINES = 400;

    private record CopyRow(
            long id,
            long localArticleId,
            long sourceTeacherAccountId,
            long sourceArticleId,
            String baseContentHash,
            String baseAnswerHash,
            String dismissedHash,
            String localTitle) {
    }

    private record SourceRow(
            long articleId,
            String title,
            String logicalPath,
            String contentHash,
            String answerHash,
            long releaseId,
            int version) {
    }

    /** 汇总里的一条。 */
    public record DigestItem(
            Long copyId,
            Long localArticleId,
            long sourceArticleId,
            String title,
            String logicalPath,
            String baseContentHash,
            String sourceContentHash) {
    }

    /** 更新汇总。 */
    public record Digest(
            int updatedCount,
            int addedCount,
            int removedCount,
            List<DigestItem> updated,
            List<DigestItem> added,
            List<DigestItem> removed) {
    }

    /** 单篇更新详情。 */
    public record UpdateDetail(
            long copyId,
            int sourceVersion,
            String baseHash,
            String sourceHash,
            boolean localModified,
            boolean answerChanged,
            String diff,
            String sourceTitle,
            String sourceLogicalPath) {
    }

    /** 应用结果；{@code backupHash} 为被覆盖前本地草稿的 blob hash。 */
    public record ApplyResult(boolean applied, String backupHash, boolean answerKeyUpdated) {
    }
}
