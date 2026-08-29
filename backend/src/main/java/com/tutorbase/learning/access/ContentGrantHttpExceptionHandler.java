package com.tutorbase.learning.access;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CurrentContentGrantController.class)
final class ContentGrantHttpExceptionHandler {

    @ExceptionHandler(LearnerContextRequired.class)
    ProblemDetail learnerContextRequired(LearnerContextRequired exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.FORBIDDEN,
                "learner_context_required",
                "账户未关联学习者",
                "当前账户未关联学习者身份，无法执行该操作。",
                request);
    }

    @ExceptionHandler(LearnerRecordMissing.class)
    ProblemDetail learnerRecordMissing(LearnerRecordMissing exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "学习者不存在",
                "当前账户关联的学习者不存在。",
                request);
    }
}
