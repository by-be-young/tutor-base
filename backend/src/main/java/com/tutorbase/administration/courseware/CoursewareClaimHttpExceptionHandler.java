package com.tutorbase.administration.courseware;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminCoursewareClaimController.class)
final class CoursewareClaimHttpExceptionHandler {

    @ExceptionHandler(CoursewareNotFound.class)
    ProblemDetail coursewareNotFound(CoursewareNotFound exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.NOT_FOUND,
                "resource_not_found",
                "资源不存在",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidCoursewareRequest.class)
    ProblemDetail invalidRequest(InvalidCoursewareRequest exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "请求参数校验失败",
                exception.getMessage(),
                request);
    }
}
