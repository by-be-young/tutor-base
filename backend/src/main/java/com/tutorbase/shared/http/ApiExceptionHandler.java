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
                "服务暂时不可用",
                "依赖的服务暂时不可用，请稍后重试。",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "请求参数校验失败",
                "请求中的部分字段不合法。",
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "请求参数校验失败",
                "请求中的部分字段不合法。",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "malformed_request",
                "请求格式错误",
                "无法读取请求内容。",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail resourceNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "资源不存在",
                "请求的资源不存在。",
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internalError(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure for {}", request.getRequestURI(), exception);
        return ApiProblem.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "服务器内部错误",
                "服务器暂时无法完成该请求，请稍后重试。",
                request);
    }
}
