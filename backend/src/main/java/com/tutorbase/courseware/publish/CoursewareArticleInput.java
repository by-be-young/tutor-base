package com.tutorbase.courseware.publish;

import java.util.List;

/** 一次发布中的一篇课件（发布接口的输入模型，同时用于校验）。 */
public record CoursewareArticleInput(
        Long articleId,
        Long legacyArticleId,
        String title,
        String series,
        String logicalPath,
        String markdown,
        List<CoursewareAnswerInput> answers) {

    /** 单题答案键。 */
    public record CoursewareAnswerInput(String questionId, String answer, boolean autoGrade) {
    }
}
