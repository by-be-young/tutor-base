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

    @ExceptionHandler(InvalidRewardMilestone.class)
    ProblemDetail invalidMilestone(InvalidRewardMilestone exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.BAD_REQUEST, "validation_failed", "奖励里程碑无效",
                "奖励里程碑必须是当前目录中的有效积分节点。", request);
    }

    @ExceptionHandler(RewardMilestoneNotReached.class)
    ProblemDetail milestoneNotReached(RewardMilestoneNotReached exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "milestone_not_reached", "奖励里程碑尚未达成",
                "当前积分不足以领取该奖励。", request);
    }

    @ExceptionHandler(RewardAlreadyClaimed.class)
    ProblemDetail alreadyClaimed(RewardAlreadyClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "reward_already_claimed", "奖励已经领取",
                "同一奖励里程碑只能领取一次。", request);
    }
}
