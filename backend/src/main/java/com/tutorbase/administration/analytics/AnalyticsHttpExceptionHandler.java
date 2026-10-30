package com.tutorbase.administration.analytics;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminAnalyticsController.class)
final class AnalyticsHttpExceptionHandler {

    @ExceptionHandler(InvalidAnalyticsRequest.class)
    ProblemDetail invalidRequest(InvalidAnalyticsRequest exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "请求参数校验失败",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(AnalyticsLearnerNotFound.class)
    ProblemDetail learnerNotFound(AnalyticsLearnerNotFound exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "学习者不存在",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(AnalyticsLearnerNotClaimed.class)
    ProblemDetail learnerNotClaimed(AnalyticsLearnerNotClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.FORBIDDEN,
                "learner_not_claimed",
                "学员未认领",
                exception.getMessage(),
                request);
    }
}
