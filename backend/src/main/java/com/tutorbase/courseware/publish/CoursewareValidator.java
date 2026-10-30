package com.tutorbase.courseware.publish;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布前校验：题目编号、作答占位符、答案键、路径、大小与脚本注入。
 *
 * <p>规则与 {@code tutor-nest-vue/docs/文章格式规范.md} 对齐；前端 {@code check-courseware}
 * 负责内容质量，这里负责「发出去之后一定能渲染、一定能判分」。
 *
 * <p>不含任何题目的纯讲义允许发布；一旦出现题干或作答占位符，两者必须一一对应。
 * 题号只作为题目标识，允许缺号（存量课件删题后不重编号，避免破坏已存答案键与错题记录）。
 */
public final class CoursewareValidator {

    private static final Pattern ANSWER_TOKEN = Pattern.compile("【@(\\d*)】");
    private static final Pattern STEM_TOKEN = Pattern.compile("【题干\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*】");
    private static final Pattern IMAGE_REF = Pattern.compile("!\\[\\[[^\\]]+]]");
    private static final Pattern EXTERNAL_IMAGE = Pattern.compile("!\\[[^\\]]*]\\((https?:)?//[^)]+\\)");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)<\\s*script|javascript\\s*:|on(error|load|click)\\s*=");

    private CoursewareValidator() {
    }

    /** @return 逐条错误；空列表表示通过 */
    public static List<String> validate(CoursewareArticleInput article, long maxBytes) {
        List<String> errors = new ArrayList<>();
        String path = article.logicalPath() == null ? "" : article.logicalPath().strip();

        if (path.isEmpty()) {
            errors.add("logicalPath 不能为空");
        } else {
            if (path.length() > 500) {
                errors.add("logicalPath 超过 500 字符");
            }
            if (!path.endsWith(".md")) {
                errors.add("logicalPath 必须是 .md 文件：" + path);
            }
            if (path.contains("\\")) {
                errors.add("logicalPath 不允许反斜杠：" + path);
            }
            if (path.startsWith("/") || path.contains(":")) {
                errors.add("logicalPath 不允许绝对路径：" + path);
            }
            if (path.contains("..")) {
                errors.add("logicalPath 不允许 ..：" + path);
            }
            if (path.contains("//")) {
                errors.add("logicalPath 不允许连续斜杠：" + path);
            }
        }

        if (article.title() == null || article.title().isBlank()) {
            errors.add("title 不能为空");
        }
        if (article.series() == null || article.series().isBlank()) {
            errors.add("series 不能为空");
        }

        String markdown = article.markdown() == null ? "" : article.markdown();
        if (markdown.isBlank()) {
            errors.add("正文不能为空");
            return errors;
        }
        if (markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
            errors.add("正文超过 " + maxBytes + " 字节上限");
        }
        if (DANGEROUS.matcher(markdown).find()) {
            errors.add("正文包含疑似脚本内容（<script / javascript: / onerror=）");
        }
        if (EXTERNAL_IMAGE.matcher(markdown).find()) {
            errors.add("正文包含外链图片，请改为站内资产 ![[文件名]]");
        }

        Set<String> answerIdSet = new LinkedHashSet<>();
        for (String id : answerIds(markdown)) {
            if (!answerIdSet.add(id)) {
                errors.add("作答占位符编号重复：【@" + id + "】");
            }
        }

        Set<String> stemIds = new LinkedHashSet<>();
        for (Matcher matcher = STEM_TOKEN.matcher(markdown); matcher.find(); ) {
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            if (end < start) {
                errors.add("题干区间非法：【题干" + start + "-" + end + "】");
                continue;
            }
            for (int id = start; id <= end; id++) {
                stemIds.add(Integer.toString(id));
            }
        }

        for (String id : answerIdSet) {
            if (!stemIds.contains(id)) {
                errors.add("第 " + id + " 题缺少题干标记【题干" + id + "】");
            }
        }
        for (String id : stemIds) {
            if (!answerIdSet.contains(id)) {
                errors.add("题干标记【题干" + id + "】没有对应的作答占位符【@" + id + "】");
            }
        }

        List<CoursewareArticleInput.CoursewareAnswerInput> answers =
                article.answers() == null ? List.of() : article.answers();
        Set<String> answeredIds = new LinkedHashSet<>();
        for (CoursewareArticleInput.CoursewareAnswerInput answer : answers) {
            if (answer.questionId() == null || answer.questionId().isBlank()) {
                errors.add("答案键的 questionId 不能为空");
                continue;
            }
            if (!answeredIds.add(answer.questionId())) {
                errors.add("答案键重复：questionId=" + answer.questionId());
            }
            if (!answerIdSet.contains(answer.questionId())) {
                errors.add("答案键引用了不存在的题目：" + answer.questionId());
            }
        }
        if (answers.size() != answerIdSet.size()) {
            errors.add("答案键数量（" + answers.size() + "）与题目数量（" + answerIdSet.size() + "）不一致");
        }

        return errors;
    }

    /** 抽取正文中的作答编号（按出现顺序，含自动编号），供发布与存量导入共用。 */
    public static List<String> answerIds(String markdown) {
        List<String> ids = new ArrayList<>();
        int autoCounter = 1;
        for (Matcher matcher = ANSWER_TOKEN.matcher(markdown == null ? "" : markdown); matcher.find(); ) {
            String raw = matcher.group(1);
            if (raw.isEmpty()) {
                while (ids.contains(Integer.toString(autoCounter))) {
                    autoCounter++;
                }
                ids.add(Integer.toString(autoCounter));
                autoCounter++;
            } else {
                ids.add(raw);
            }
        }
        return ids;
    }

    /** 逻辑路径规范化：统一分隔符、去掉首尾空白与多余前导斜杠。 */
    public static String normalizePath(String logicalPath) {
        return logicalPath == null ? "" : logicalPath.strip().replace('\\', '/').replaceAll("^/+", "");
    }

    /** 是否站内图片引用（供发布提示使用）。 */
    public static boolean hasLocalImages(String markdown) {
        return IMAGE_REF.matcher(markdown == null ? "" : markdown).find();
    }

    /** 便于日志输出的小写摘要。 */
    static String summarize(List<String> errors) {
        return String.join("; ", errors).toLowerCase(Locale.ROOT);
    }
}
