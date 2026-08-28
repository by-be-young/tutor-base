package com.tutorbase.checkin;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CheckInController.class)
final class CheckInHttpExceptionHandler {

    @ExceptionHandler(LearnerContextRequired.class)
    ProblemDetail learnerContextRequired(LearnerContextRequired exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.FORBIDDEN,
                "learner_context_required",
                "Learner context required",
                "The current account is not linked to a learner.",
                request);
    }
}
