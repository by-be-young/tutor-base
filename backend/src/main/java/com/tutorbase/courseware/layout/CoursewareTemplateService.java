package com.tutorbase.courseware.layout;

import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模板库：新建课件时的骨架来源。
 *
 * <p>设计稿原本把模板放在仓库 {@code templates/<科目>/<类型>.md}，运行时后端读不到仓库文件，
 * 因此落到 {@code course_template}：{@code subject IS NULL} 为全科通用模板。
 */
@Service
public class CoursewareTemplateService {

    private final JdbcClient jdbc;

    CoursewareTemplateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 可用模板：通用模板 + 指定科目的模板。 */
    @Transactional(readOnly = true)
    public List<Template> list(String subject) {
        return jdbc.sql("""
                SELECT id, subject, kind, name
                FROM public.course_template
                WHERE active
                  AND (subject IS NULL OR cast(:subject as text) IS NULL OR subject = :subject)
                ORDER BY (subject IS NULL) DESC, kind, name
                """)
                .param("subject", subject)
                .query((rs, rowNum) -> new Template(
                        rs.getLong("id"),
                        rs.getString("subject"),
                        rs.getString("kind"),
                        rs.getString("name")))
                .list();
    }

    /** 取模板正文。 */
    @Transactional(readOnly = true)
    public String markdown(long templateId) {
        return jdbc.sql("SELECT markdown FROM public.course_template WHERE id = :id AND active")
                .param("id", templateId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new CoursewareFailures.NotFound("模板不存在或已停用：" + templateId));
    }

    /** 把当前正文存成模板（同名同科目同类型时覆盖）。 */
    @Transactional
    public long save(String subject, String kind, String name, String markdown) {
        if (kind == null || !List.of("new_lesson", "practice", "review", "topic").contains(kind)) {
            throw new CoursewareFailures.Validation(
                    List.of("kind 只能是 new_lesson / practice / review / topic"));
        }
        if (name == null || name.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("模板名不能为空"));
        }
        if (markdown == null || markdown.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("模板正文不能为空"));
        }
        return jdbc.sql("""
                INSERT INTO public.course_template (subject, kind, name, markdown, updated_at)
                VALUES (:subject, :kind, :name, :markdown, now())
                ON CONFLICT (coalesce(subject, '*'), kind, name) DO UPDATE
                    SET markdown = EXCLUDED.markdown, active = true, updated_at = now()
                RETURNING id
                """)
                .param("subject", subject == null || subject.isBlank() ? null : subject)
                .param("kind", kind)
                .param("name", name.strip())
                .param("markdown", markdown)
                .query(Long.class)
                .single();
    }

    @Transactional
    public void deactivate(long templateId) {
        int updated = jdbc.sql("UPDATE public.course_template SET active = false, updated_at = now() WHERE id = :id")
                .param("id", templateId)
                .update();
        if (updated == 0) {
            throw new CoursewareFailures.NotFound("模板不存在：" + templateId);
        }
    }

    /** 模板列表项（不含正文）。 */
    public record Template(long id, String subject, String kind, String name) {
    }
}
