package com.tutorbase.identity;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        IdentityController.class,
        AdminAccountActivationController.class,
        AdminLearnerPasswordController.class,
        AdminLearnerImpersonationController.class,
        TeacherApplicationController.class
})
final class IdentityHttpExceptionHandler {
    @ExceptionHandler(IdentityRateLimiter.RateLimited.class)
    ResponseEntity<ProblemDetail> rateLimited(
            IdentityRateLimiter.RateLimited exception,
            HttpServletRequest request) {
        ProblemDetail problem = ApiProblem.create(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "操作过于频繁",
                "该地址的登录尝试过于频繁，请稍后再试。", request);
        long retryAfter = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfter))
                .body(problem);
    }

    @ExceptionHandler(IdentityService.InvalidCredentials.class)
    ProblemDetail invalidCredentials(IdentityService.InvalidCredentials exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.UNAUTHORIZED, "invalid_credentials", "用户名或密码错误",
                "用户名或密码不正确，请检查后重试。", request);
    }

    @ExceptionHandler(IdentityService.InvalidActivation.class)
    ProblemDetail invalidActivation(IdentityService.InvalidActivation exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.UNAUTHORIZED, "invalid_activation_token", "激活凭证无效",
                "激活凭证无效、已过期或已使用，或该账户无需激活。", request);
    }

    @ExceptionHandler(IdentityService.UsernameConflict.class)
    ProblemDetail usernameConflict(IdentityService.UsernameConflict exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "username_conflict", "用户名已被占用",
                "该用户名已被注册，请更换后重试。", request);
    }

    @ExceptionHandler(IdentityService.AccountNotFound.class)
    ProblemDetail accountNotFound(IdentityService.AccountNotFound exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.NOT_FOUND, "resource_not_found", "账户不存在",
                "请求的账户不存在。", request);
    }

    @ExceptionHandler(IdentityService.LearnerClaimRequired.class)
    ProblemDetail learnerClaimRequired(
            IdentityService.LearnerClaimRequired exception,
            HttpServletRequest request) {
        return ApiProblem.create(
                HttpStatus.FORBIDDEN,
                "learner_not_claimed",
                "学员未认领",
                "只能设置自己认领的学员密码或进入其账号。",
                request);
    }

    @ExceptionHandler(IdentityService.AccountStateConflict.class)
    ProblemDetail accountStateConflict(
            IdentityService.AccountStateConflict exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "state_conflict", "账户状态不允许该操作",
                "当前账户状态不允许执行此操作。", request);
    }

    @ExceptionHandler(TeacherApplicationService.UsernameConflict.class)
    ProblemDetail teacherUsernameConflict(
            TeacherApplicationService.UsernameConflict exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "username_conflict", "用户名已被占用",
                "该用户名已有账户或待审核申请，请更换后重试。", request);
    }

    @ExceptionHandler(TeacherApplicationService.ApplicationNotFound.class)
    ProblemDetail teacherApplicationNotFound(
            TeacherApplicationService.ApplicationNotFound exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.NOT_FOUND, "resource_not_found", "申请不存在",
                "请求的教师注册申请不存在。", request);
    }

    @ExceptionHandler(TeacherApplicationService.ApplicationStateConflict.class)
    ProblemDetail teacherApplicationStateConflict(
            TeacherApplicationService.ApplicationStateConflict exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "state_conflict", "申请已处理",
                "该申请已通过或已驳回，不能重复审核。", request);
    }

    @ExceptionHandler(TeacherApplicationService.InvalidRejectionReason.class)
    ProblemDetail invalidRejectionReason(
            TeacherApplicationService.InvalidRejectionReason exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.BAD_REQUEST, "validation_failed", "驳回理由不能为空",
                "驳回申请时必须填写理由。", request);
    }

    @ExceptionHandler(TeacherApplicationService.InvalidApplicationStatus.class)
    ProblemDetail invalidApplicationStatus(
            TeacherApplicationService.InvalidApplicationStatus exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.BAD_REQUEST, "validation_failed", "status 参数无效",
                "status 必须是 pending、approved、rejected 或 all。", request);
    }

    @ExceptionHandler(TeacherApplicationService.SuperAdministratorRequired.class)
    ProblemDetail superAdministratorRequired(
            TeacherApplicationService.SuperAdministratorRequired exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "forbidden", "需要超级管理员权限",
                "只有超级管理员可以查看和审核教师注册申请。", request);
    }
}
