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
        AdminLearnerPasswordController.class
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

    @ExceptionHandler(IdentityService.AccountStateConflict.class)
    ProblemDetail accountStateConflict(
            IdentityService.AccountStateConflict exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "state_conflict", "账户状态不允许该操作",
                "当前账户状态不允许执行此操作。", request);
    }
}
