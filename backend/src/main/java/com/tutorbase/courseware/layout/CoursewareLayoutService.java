package com.tutorbase.courseware.layout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 布局注册表：编辑器与发布校验共用。
 *
 * <p>布局指令写在 markdown 的 HTML 注释里（{@code <!-- @layout:A -->}），对普通 Markdown
 * 渲染与既有 {@code 【题干N】}/{@code 【@N】} 解析都没有影响。没有指令的课件照旧可发布。
 */
@Service
public class CoursewareLayoutService {

    private static final Pattern LAYOUT_DIRECTIVE =
            Pattern.compile("<!--\\s*@layout:\\s*([A-Za-z0-9_-]+)\\s*-->");
    private static final Pattern TEMPLATE_RESIDUE = Pattern.compile("\\{\\{[^}]*}}");
    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\n([\\s\\S]*?)\\n---\\n");
    private static final Pattern DEFAULT_LAYOUT = Pattern.compile("(?m)^defaultLayout:\\s*([A-Za-z0-9_-]+)\\s*$");

    private final JdbcClient jdbc;

    CoursewareLayoutService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Layout> active() {
        return jdbc.sql("""
                SELECT layout_id, name, component, allowed_blocks, version
                FROM public.course_layout
                WHERE active
                ORDER BY layout_id
                """)
                .query((rs, rowNum) -> new Layout(
                        rs.getString("layout_id"),
                        rs.getString("name"),
                        rs.getString("component"),
                        java.util.Arrays.asList((String[]) rs.getArray("allowed_blocks").getArray()),
                        rs.getInt("version")))
                .list();
    }

    /** 正文里出现的全部布局 id（含 front-matter 的 defaultLayout）。 */
    public static Set<String> layoutIdsOf(String markdown) {
        Set<String> ids = new LinkedHashSet<>();
        String content = markdown == null ? "" : markdown;
        Matcher matcher = LAYOUT_DIRECTIVE.matcher(content);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        Matcher frontMatter = FRONT_MATTER.matcher(content);
        if (frontMatter.find()) {
            Matcher fallback = DEFAULT_LAYOUT.matcher(frontMatter.group(1));
            if (fallback.find()) {
                ids.add(fallback.group(1));
            }
        }
        return ids;
    }

    /**
     * 发布前校验：布局 id 必须已注册且启用、不能残留模板变量、每个 section 不能为空。
     *
     * <p>纯 Markdown（无布局指令、无 front-matter）始终通过。
     */
    @Transactional(readOnly = true)
    public List<String> validate(String markdown) {
        List<String> errors = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return errors;
        }
        Matcher residue = TEMPLATE_RESIDUE.matcher(markdown);
        if (residue.find()) {
            errors.add("正文残留模板变量：" + residue.group());
        }
        Set<String> ids = layoutIdsOf(markdown);
        if (ids.isEmpty()) {
            return errors;
        }
        Set<String> known = new LinkedHashSet<>();
        for (Layout layout : active()) {
            known.add(layout.layoutId());
        }
        for (String id : ids) {
            if (!known.contains(id)) {
                errors.add("布局 " + id + " 未注册或已停用");
            }
        }
        errors.addAll(emptySections(markdown));
        return errors;
    }

    /** 每个 `# ` 标题之后、下一条布局指令/分隔线之前必须有正文。 */
    public static List<String> emptySections(String markdown) {
        List<String> errors = new ArrayList<>();
        String[] lines = (markdown == null ? "" : markdown).split("\n", -1);
        String currentHeading = null;
        boolean hasBody = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith("# ")) {
                if (currentHeading != null && !hasBody) {
                    errors.add("section 为空：" + currentHeading);
                }
                currentHeading = trimmed.substring(2).strip();
                hasBody = false;
                continue;
            }
            if (trimmed.isEmpty() || "---".equals(trimmed) || LAYOUT_DIRECTIVE.matcher(trimmed).matches()) {
                continue;
            }
            if (currentHeading != null) {
                hasBody = true;
            }
        }
        if (currentHeading != null && !hasBody) {
            errors.add("section 为空：" + currentHeading);
        }
        return errors;
    }

    /** 新建课件时使用的骨架。 */
    public static String skeleton(String title, String layoutId) {
        String safeTitle = title == null || title.isBlank() ? "新课件" : title.strip();
        String layout = layoutId == null || layoutId.isBlank() ? "A" : layoutId;
        StringBuilder builder = new StringBuilder();
        builder.append("---\n");
        builder.append("title: ").append(safeTitle).append('\n');
        builder.append("defaultLayout: ").append(layout).append('\n');
        builder.append("---\n\n");
        builder.append("<!-- @layout:").append(layout).append(" -->\n");
        builder.append("# ").append(safeTitle).append("\n\n");
        if ("B".equals(layout)) {
            builder.append("【题干1】在这里写题干\n\n【@1】\n");
        } else {
            builder.append("在这里写正文\n");
        }
        return builder.toString();
    }

    /** 注册表里的一条布局。 */
    public record Layout(
            String layoutId,
            String name,
            String component,
            List<String> allowedBlocks,
            int version) {
    }
}
