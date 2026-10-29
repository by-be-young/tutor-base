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
                ? "请求格式错误"
                : "请求参数校验失败";
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
                "学习者不存在",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(LearnerUsernameConflict.class)
    ProblemDetail usernameConflict(LearnerUsernameConflict exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.CONFLICT,
                "username_conflict",
                "用户名已被占用",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(LearnerAlreadyClaimed.class)
    ProblemDetail learnerAlreadyClaimed(LearnerAlreadyClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.CONFLICT,
                "learner_already_claimed",
                "学习者已被其他管理员认领",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(LearnerCoursewareNotClaimed.class)
    ProblemDetail learnerCoursewareNotClaimed(
            LearnerCoursewareNotClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.FORBIDDEN,
                "courseware_not_claimed",
                "课件未认领",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(AdministratorLearnerProtected.class)
    ProblemDetail administratorLearnerProtected(
            AdministratorLearnerProtected exception, HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.CONFLICT,
                "administrator_learner_protected",
                "管理员学习者受保护",
                exception.getMessage(),
                request);
    }
}
