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
        ProblemDetail problem = ApiProblem.create(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many requests",
                "Too many authentication attempts were made from this address.", request);
        long retryAfter = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfter))
                .body(problem);
    }

    @ExceptionHandler(IdentityService.InvalidCredentials.class)
    ProblemDetail invalidCredentials(IdentityService.InvalidCredentials exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid credentials",
                "The supplied credentials are invalid.", request);
    }

    @ExceptionHandler(IdentityService.InvalidActivation.class)
    ProblemDetail invalidActivation(IdentityService.InvalidActivation exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.UNAUTHORIZED, "invalid_activation_token", "Invalid activation token",
                "The activation credential is invalid, expired, consumed, or the account is not pending.", request);
    }

    @ExceptionHandler(IdentityService.AccountNotFound.class)
    ProblemDetail accountNotFound(IdentityService.AccountNotFound exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.NOT_FOUND, "resource_not_found", "Account not found",
                "The requested account does not exist.", request);
    }

    @ExceptionHandler(IdentityService.AccountStateConflict.class)
    ProblemDetail accountStateConflict(
            IdentityService.AccountStateConflict exception,
            HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "state_conflict", "Account state conflict",
                "The requested account state does not allow this operation.", request);
    }
}
