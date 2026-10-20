package com.tutorbase.rewards;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RewardController.class)
final class RewardHttpExceptionHandler {

    @ExceptionHandler(LearnerContextRequired.class)
    ProblemDetail learnerContextRequired(LearnerContextRequired exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "learner_context_required", "账户未关联学习者",
                "当前账户未关联学习者身份，无法执行该操作。", request);
    }

    @ExceptionHandler(RewardInsufficientPoints.class)
    ProblemDetail insufficientPoints(RewardInsufficientPoints exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "insufficient_points", "积分不足",
                "当前积分不足以完成抽卡。", request);
    }
}
