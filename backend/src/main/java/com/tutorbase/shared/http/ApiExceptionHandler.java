package com.tutorbase.shared.http;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
final class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dependencyUnavailable(DataAccessException exception, HttpServletRequest request) {
        LOGGER.warn(
                "Database request failed for {} ({})",
                request.getRequestURI(),
                exception.getClass().getSimpleName());
        return ApiProblem.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "dependency_unavailable",
                "Dependency unavailable",
                "A required dependency is temporarily unavailable.",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Request validation failed",
                "One or more request fields are invalid.",
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Request validation failed",
                "One or more request fields are invalid.",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "malformed_request",
                "Malformed request",
                "The request body could not be read.",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail resourceNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "Resource not found",
                "The requested resource does not exist.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internalError(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure for {}", request.getRequestURI(), exception);
        return ApiProblem.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Internal server error",
                "The server could not complete the request.",
                request);
    }
}
