package com.tutorbase.courseware.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从课件正文里抽取某道题的题干（与 `tutor-nest-vue/docs/文章格式规范.md` 对齐）。
 *
 * <p>用于写入 {@code stem_snapshot}：错题本与作答记录从此不再依赖"运行时按课件原文现场解析"，
 * 课件后续改版也不会改变历史记录里的题干。规则：
 * <ul>
 *   <li>{@code 【题干N】} 之后、下一个占位符/题干标记/独占一行的 {@code ---} 之前；</li>
 *   <li>{@code 【题干N-M】} 的共享材料适用于 N..M 的每一题；</li>
 *   <li>丢弃空行、{@code #} 标题、{@code >} 引用前缀、{@code **} 加粗标记；</li>
 *   <li>超过 400 字符按行截断。</li>
 * </ul>
 */
public final class CoursewareStemExtractor {

    private static final Pattern STEM_TOKEN = Pattern.compile("【题干\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*】");
    private static final Pattern ANSWER_TOKEN = Pattern.compile("【@(\\d*)】");
    private static final int MAX_LENGTH = 400;

    private CoursewareStemExtractor() {
    }

    /** @return 题干文本；定位不到时返回空串 */
    public static String extract(String markdown, String questionId) {
        if (markdown == null || questionId == null || questionId.isBlank()) {
            return "";
        }
        StringBuilder collected = new StringBuilder();
        boolean capturing = false;
        for (String rawLine : markdown.split("\n", -1)) {
            String line = rawLine.strip();
            Matcher stemMatcher = STEM_TOKEN.matcher(line);
            if (stemMatcher.find()) {
                boolean coversThis = covers(stemMatcher, questionId);
                if (capturing && coversThis) {
                    // 【题干N-M】共享材料 + 各自的【题干N】小问：两段都属于该题题干
                    appendCleaned(collected, clean(line.substring(stemMatcher.end())));
                    continue;
                }
                if (capturing) {
                    // 遇到别的题的题干：共享材料已收下，本题正文可能还在后面（【题干N-M】+【题干N】写法）
                    capturing = false;
                    continue;
                }
                capturing = coversThis;
                if (capturing) {
                    // 标题与首句常写在同一行：标记之后的内容也属于题干
                    appendCleaned(collected, clean(line.substring(stemMatcher.end())));
                }
                continue;
            }
            if (ANSWER_TOKEN.matcher(line).matches()) {
                if (capturing) {
                    break;
                }
                continue;
            }
            if (!capturing) {
                continue;
            }
            if ("---".equals(line)) {
                break;
            }
            String cleaned = clean(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            appendCleaned(collected, cleaned);
            if (collected.length() >= MAX_LENGTH) {
                break;
            }
        }
        return truncate(collected.toString());
    }

    private static void appendCleaned(StringBuilder collected, String cleaned) {
        if (cleaned.isEmpty()) {
            return;
        }
        if (collected.length() > 0) {
            collected.append('\n');
        }
        collected.append(cleaned);
    }

    private static boolean covers(Matcher matcher, String questionId) {
        try {
            int id = Integer.parseInt(questionId.strip());
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            return id >= start && id <= Math.max(start, end);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** 正文里的全部题号（按出现顺序；`【@】` 自动编号从 1 递增）。 */
    public static List<String> questionIds(String markdown) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = ANSWER_TOKEN.matcher(markdown == null ? "" : markdown);
        int autoCounter = 1;
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.isEmpty()) {
                while (ids.contains(String.valueOf(autoCounter))) {
                    autoCounter++;
                }
                ids.add(String.valueOf(autoCounter));
                autoCounter++;
            } else {
                ids.add(raw);
            }
        }
        return ids;
    }

    private static String clean(String line) {
        String value = line.strip();
        while (value.startsWith(">")) {
            value = value.substring(1).strip();
        }
        if (value.startsWith("#")) {
            return "";
        }
        if (value.startsWith("**") && value.endsWith("**") && value.length() > 4) {
            value = value.substring(2, value.length() - 2).strip();
        }
        return value;
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf('\n', MAX_LENGTH);
        if (cut < 0) {
            cut = MAX_LENGTH;
        }
        return text.substring(0, cut).strip() + "…";
    }
}
