package com.tutorbase.courseware;

/**
 * 课件标题与正文首标题的一致性处理。
 *
 * <p>目录树里的名称来自发布元数据，阅读器正文里的名称来自 Markdown 的首个 {@code # } 标题。
 * 只改文件名会让两处不一致，所以移动/重命名时把正文里等价的标题一起改掉。
 *
 * <p>等价判断会忽略开头的编号（如 {@code 3 虚拟语气} 与 {@code 虚拟语气}），
 * 这样历史遗留的「文件名改了、正文没改」也能在下次发布时自动修复。
 */
public final class CoursewareTitles {

    private CoursewareTitles() {
    }

    /** 把 frontmatter 的 {@code title:} 与首个一级标题改成与新标题一致；不需要改动时原样返回。 */
    public static String retitle(String markdown, String newTitle) {
        if (markdown == null || newTitle == null || newTitle.isBlank()) {
            return markdown;
        }
        String title = newTitle.strip();
        String[] lines = markdown.split("\n", -1);
        boolean inFrontMatter = lines.length > 0 && "---".equals(lines[0].strip());
        int firstHeading = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (inFrontMatter) {
                if (i > 0 && "---".equals(trimmed)) {
                    inFrontMatter = false;
                    continue;
                }
                if (trimmed.startsWith("title:")) {
                    String existing = unquote(trimmed.substring("title:".length()).strip());
                    if (sameTitle(existing, title)) {
                        lines[i] = "title: " + title;
                    }
                }
                continue;
            }
            if (trimmed.startsWith("# ")) {
                firstHeading = i;
                break;
            }
        }
        if (firstHeading >= 0) {
            String existing = lines[firstHeading].strip().substring(2).strip();
            if (sameTitle(existing, title)) {
                lines[firstHeading] = "# " + title;
            }
        }
        return String.join("\n", lines);
    }

    /** 从正文里取标题：优先 frontmatter 的 {@code title:}，否则首个一级标题。 */
    public static String titleOf(String markdown) {
        if (markdown == null) {
            return "";
        }
        String[] lines = markdown.split("\n", -1);
        boolean inFrontMatter = lines.length > 0 && "---".equals(lines[0].strip());
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (inFrontMatter) {
                if (i > 0 && "---".equals(trimmed)) {
                    inFrontMatter = false;
                    continue;
                }
                if (trimmed.startsWith("title:")) {
                    String title = unquote(trimmed.substring("title:".length()).strip());
                    if (!title.isBlank()) {
                        return title;
                    }
                }
                continue;
            }
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).strip();
            }
        }
        return "";
    }

    /** 标题等价：去掉开头编号与所有空白后比较。 */
    public static boolean sameTitle(String left, String right) {
        return normalizeTitle(left).equals(normalizeTitle(right));
    }

    static String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .replaceFirst("^[0-9０-９]+[\\s._\\-—、]*", "")
                .replaceAll("\\s+", "");
    }

    /** 从逻辑路径取文件名（去目录、去 .md 后缀）作为标题。 */
    public static String fromLogicalPath(String logicalPath) {
        String name = logicalPath == null ? "" : logicalPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
