package com.tutorbase.administration.learner;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminLearnerController.class)
final class LearnerHttpExceptionHandler {

    @ExceptionHandler(InvalidLearnerPageRequest.class)
    ProblemDetail invalidPageRequest(InvalidLearnerPageRequest exception, HttpServletRequest request) {
        String title = exception.code().equals("malformed_request")
                ? "Malformed request"
                : "Request validation failed";
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                exception.code(),
                title,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(LearnerNotFound.class)
    ProblemDetail learnerNotFound(LearnerNotFound exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "Learner not found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(LearnerUsernameConflict.class)
    ProblemDetail usernameConflict(LearnerUsernameConflict exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.CONFLICT,
                "username_conflict",
                "Username conflict",
                exception.getMessage(),
                request);
    }
}
