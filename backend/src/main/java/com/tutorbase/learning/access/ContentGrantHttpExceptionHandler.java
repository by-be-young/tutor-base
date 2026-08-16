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
                "Learner context required",
                "The current account is not linked to a learner.",
                request);
    }

    @ExceptionHandler(LearnerRecordMissing.class)
    ProblemDetail learnerRecordMissing(LearnerRecordMissing exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "Learner not found",
                "The learner linked to the current account does not exist.",
                request);
    }
}
