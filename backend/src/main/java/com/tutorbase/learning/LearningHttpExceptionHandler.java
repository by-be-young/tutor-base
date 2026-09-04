package com.tutorbase.learning;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LearningController.class)
final class LearningHttpExceptionHandler {

    @ExceptionHandler(LearningFailures.LearnerContextRequired.class)
    ProblemDetail learnerRequired(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "learner_context_required", "账户未关联学习者",
                "当前账户未关联学习者身份，无法执行该操作。", request);
    }

    @ExceptionHandler(LearningFailures.ContentAccessDenied.class)
    ProblemDetail contentDenied(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "content_access_denied", "文章未授权",
                "当前学习者没有访问该文章学习数据的权限。", request);
    }

    @ExceptionHandler(LearningFailures.SubmissionAlreadyReviewed.class)
    ProblemDetail alreadyReviewed(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "submission_already_reviewed", "作答已经批阅",
                "已批阅的作答不能再次提交。", request);
    }

    @ExceptionHandler(LearningFailures.SubmissionNotFound.class)
    ProblemDetail submissionNotFound(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.NOT_FOUND, "submission_not_found", "作答不存在",
                "没有找到指定学习者的作答记录。", request);
    }
}
