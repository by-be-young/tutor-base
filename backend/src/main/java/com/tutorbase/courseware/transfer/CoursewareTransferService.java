package com.tutorbase.courseware.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.storage.CoursewareStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导出 / 导入 zip。
 *
 * <p>导出：{@code manifest.json} + 每篇 {@code articles/<逻辑路径>.md} + {@code answers.json}。
 * 导入即独立副本，不与任何来源建立 {@code course_copy} 关系（设计稿 §7.6）；同路径默认跳过，
 * {@code overwrite=true} 时覆盖草稿（原草稿内容写进报告，可回退）。
 */
@Service
public class CoursewareTransferService {

    private static final long FIRST_ARTICLE_ID = 1000;
    private static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    private static final int MAX_ENTRIES = 500;

    private final JdbcClient jdbc;
    private final CoursewareStorage storage;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    CoursewareTransferService(JdbcClient jdbc, CoursewareStorage storage,
            tools.jackson.databind.ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /** 导出整科（{@code articleIds} 为空）或指定篇目。会写一条 export 审计日志，因此不是只读事务。 */
    @Transactional
    public Export export(long teacherId, String subject, List<Long> articleIds) {
        List<ExportRow> rows = jdbc.sql("""
                SELECT ca.article_id, ca.legacy_article_id, ca.title, ca.series, ca.logical_path, ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId
                  AND (:subject IS NULL OR h.subject = :subject)
                ORDER BY ca.logical_path
                """)
                .param("teacherId", teacherId)
                .param("subject", subject)
                .query((rs, rowNum) -> new ExportRow(
                        rs.getLong("article_id"),
                        rs.getObject("legacy_article_id", Long.class),
                        rs.getString("title"),
                        rs.getString("series"),
                        rs.getString("logical_path"),
                        rs.getString("content_hash")))
                .list();

        List<Long> wanted = articleIds == null || articleIds.isEmpty() ? List.of() : articleIds;
        List<ExportRow> selected = rows.stream()
                .filter(row -> wanted.isEmpty() || wanted.contains(row.articleId()))
                .toList();
        if (selected.isEmpty()) {
            throw new CoursewareFailures.NotFound("没有可导出的课件");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("teacherId", teacherId);
        manifest.put("exportedAt", java.time.Instant.now().toString());

        List<Map<String, Object>> articles = new ArrayList<>();
        Map<String, Map<String, Object>> answersByPath = new LinkedHashMap<>();
        byte[] zip;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream zipStream = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (ExportRow row : selected) {
                byte[] markdown = storage.getBlob(teacherId, row.contentHash());
                if (markdown == null) {
                    continue;
                }
                zipStream.putNextEntry(new ZipEntry("articles/" + row.logicalPath()));
                zipStream.write(markdown);
                zipStream.closeEntry();

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("articleId", row.articleId());
                entry.put("legacyArticleId", row.legacyArticleId());
                entry.put("title", row.title());
                entry.put("series", row.series());
                entry.put("logicalPath", row.logicalPath());
                articles.add(entry);
                answersByPath.put(row.logicalPath(), answerMap(row.articleId()));
            }
            manifest.put("articles", articles);
            writeEntry(zipStream, "manifest.json", json(manifest));
            writeEntry(zipStream, "answers.json", json(answersByPath));
            zipStream.finish();
            zip = buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成导出包失败", exception);
        }
        log(teacherId, null, "export", "{\"articles\":" + articles.size() + "}");
        return new Export("courseware-" + (subject == null ? "all" : subject) + ".zip", zip);
    }

    /**
     * 导入 zip，全部篇目先落草稿，由老师确认后发布。
     *
     * @param overwrite 同路径已存在时是否覆盖草稿
     */
    @Transactional
    public ImportReport importZip(long teacherId, InputStream input, boolean overwrite) {
        Map<String, String> markdownByPath = new LinkedHashMap<>();
        readEntries(input, markdownByPath);

        List<ImportItem> items = new ArrayList<>();
        long nextId = nextArticleId(teacherId);
        for (Map.Entry<String, String> entry : markdownByPath.entrySet()) {
            String path = entry.getKey();
            String markdown = entry.getValue();
            if (markdown.isBlank()) {
                items.add(new ImportItem(path, null, "skipped", "正文为空"));
                continue;
            }
            Long existing = existingArticleAtPath(teacherId, path);
            if (existing != null && !overwrite) {
                items.add(new ImportItem(path, existing, "skipped", "同名路径已存在"));
                continue;
            }
            long articleId = existing != null ? existing : nextId++;
            saveDraft(teacherId, articleId, markdown);
            items.add(new ImportItem(path, articleId, existing != null ? "overwritten" : "imported", null));
        }

        long imported = items.stream().filter(item -> "imported".equals(item.status())).count();
        long overwritten = items.stream().filter(item -> "overwritten".equals(item.status())).count();
        long skipped = items.stream().filter(item -> "skipped".equals(item.status())).count();
        log(teacherId, null, "import",
                "{\"imported\":" + imported + ",\"overwritten\":" + overwritten + ",\"skipped\":" + skipped + "}");
        return new ImportReport((int) imported, (int) overwritten, (int) skipped, items);
    }

    private void readEntries(InputStream input, Map<String, String> markdownByPath) {
        try (ZipInputStream zipStream = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new CoursewareFailures.Validation(List.of("压缩包条目过多（上限 " + MAX_ENTRIES + "）"));
                }
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.endsWith(".md")) {
                    continue;
                }
                String path = name.startsWith("articles/") ? name.substring("articles/".length()) : name;
                if (path.isBlank() || path.contains("..") || path.startsWith("/")) {
                    throw new CoursewareFailures.Validation(List.of("非法的条目路径：" + name));
                }
                markdownByPath.put(path, readText(zipStream));
            }
        } catch (IOException exception) {
            throw new CoursewareFailures.Validation(List.of("无法读取压缩包：" + exception.getMessage()));
        }
        if (markdownByPath.isEmpty()) {
            throw new CoursewareFailures.Validation(List.of("压缩包里没有 .md 课件"));
        }
    }

    private String readText(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(chunk)) > 0) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new CoursewareFailures.Validation(List.of("单个课件超过 2 MB 上限"));
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> answerMap(long articleId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT question_id, answer_text, auto_grade
                FROM public.article_answer_keys
                WHERE blog_id = :blogId
                ORDER BY question_id
                """)
                .param("blogId", articleId)
                .query((rs, rowNum) -> Map.entry(rs.getString("question_id"),
                        Map.of("answer", rs.getString("answer_text"), "autoGrade", rs.getBoolean("auto_grade"))))
                .list()
                .forEach(entry -> answers.put(entry.getKey(), entry.getValue()));
        return answers;
    }

    private Long existingArticleAtPath(long teacherId, String path) {
        return jdbc.sql("""
                SELECT d.article_id
                FROM public.course_draft AS d
                JOIN public.course_release_head AS h ON h.teacher_account_id = d.teacher_account_id
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = d.teacher_account_id
                 AND ca.article_id = d.article_id
                WHERE d.teacher_account_id = :teacherId AND ca.logical_path = :path
                UNION ALL
                SELECT ca.article_id
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = :teacherId AND ca.logical_path = :path
                LIMIT 1
                """)
                .param("teacherId", teacherId)
                .param("path", path)
                .query(Long.class)
                .optional()
                .orElse(null);
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
        return max == null ? FIRST_ARTICLE_ID : Math.max(max + 1, FIRST_ARTICLE_ID);
    }

    private void log(long teacherId, Long articleId, String action, String detailJson) {
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

    private static void writeEntry(ZipOutputStream zipStream, String name, byte[] content) throws IOException {
        zipStream.putNextEntry(new ZipEntry(name));
        zipStream.write(content);
        zipStream.closeEntry();
    }

    private byte[] json(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("序列化导出包清单失败", exception);
        }
    }

    private record ExportRow(
            long articleId, Long legacyArticleId, String title, String series,
            String logicalPath, String contentHash) {
    }

    /** 导出包。 */
    public record Export(String fileName, byte[] content) {
    }

    /** 导入报告里的一条。 */
    public record ImportItem(String logicalPath, Long articleId, String status, String message) {
    }

    /** 导入报告。 */
    public record ImportReport(int imported, int overwritten, int skipped, List<ImportItem> items) {
    }
}
