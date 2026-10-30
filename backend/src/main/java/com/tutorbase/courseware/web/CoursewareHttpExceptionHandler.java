package com.tutorbase.courseware.web;

import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 课件托管的 Problem Details 映射，错误码见 docs/api-standards.md 第 9 节。 */
@RestControllerAdvice
class CoursewareHttpExceptionHandler {

    @ExceptionHandler(CoursewareFailures.NotFound.class)
    ProblemDetail notFound(CoursewareFailures.NotFound exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "courseware_not_found", "课件不存在", exception.getMessage(), request);
    }

    @ExceptionHandler(CoursewareFailures.Forbidden.class)
    ProblemDetail forbidden(CoursewareFailures.Forbidden exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "courseware_forbidden", "无权访问课件", exception.getMessage(), request);
    }

    @ExceptionHandler(CoursewareFailures.VersionConflict.class)
    ProblemDetail conflict(CoursewareFailures.VersionConflict exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "courseware_release_conflict", "发布版本冲突", exception.getMessage(), request);
    }

    @ExceptionHandler(CoursewareFailures.Validation.class)
    ProblemDetail validation(CoursewareFailures.Validation exception, HttpServletRequest request) {
        List<String> errors = exception.errors();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "courseware_validation_failed",
                "课件发布校验失败", "有 " + errors.size() + " 处内容不符合发布要求。", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request) {
        return ApiProblem.create(status, code, title, detail == null ? title : detail, request);
    }
}
