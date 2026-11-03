package com.tutorbase.courseware.publish;

import java.util.List;

/**
 * 一次发布中的一篇课件（发布接口的输入模型，同时用于校验）。
 *
 * <p>{@code contentHash}/{@code answerHash}/{@code questionCount} 只在「结转」时使用：
 * 已发布篇目的本地正文 blob 丢失（换机、卷未挂载、容器重建）时，发布按上一版哈希原样结转，
 * 不重算正文，避免一次发布把整个科目的课件清空。正常发布这三个字段为 {@code null}。
 */
public record CoursewareArticleInput(
        Long articleId,
        Long legacyArticleId,
        String title,
        String series,
        String logicalPath,
        String markdown,
        List<CoursewareAnswerInput> answers,
        String contentHash,
        String answerHash,
        Integer questionCount) {

    /** 正常发布：正文参与校验与哈希计算。 */
    public CoursewareArticleInput(
            Long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String markdown,
            List<CoursewareAnswerInput> answers) {
        this(articleId, legacyArticleId, title, series, logicalPath, markdown, answers, null, null, null);
    }

    /** 结转：正文 blob 缺失，按上一版哈希保留篇目，不写 blob、不重算答案。 */
    public static CoursewareArticleInput carried(
            Long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            String answerHash,
            Integer questionCount) {
        return new CoursewareArticleInput(articleId, legacyArticleId, title, series, logicalPath,
                null, null, contentHash, answerHash, questionCount);
    }

    /** 是否结转（正文缺失、沿用上一版哈希）。 */
    public boolean carryOver() {
        return markdown == null;
    }

    /** 单题答案键。 */
    public record CoursewareAnswerInput(String questionId, String answer, boolean autoGrade) {
    }
}
