package com.tutorbase.growth;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GrowthController.class)
final class GrowthHttpExceptionHandler {

    @ExceptionHandler(LearnerContextRequired.class)
    ProblemDetail learnerContextRequired(LearnerContextRequired exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "learner_context_required", "账户未关联学习者",
                "当前账户未关联学习者身份，无法访问成长中心。", request);
    }

    @ExceptionHandler(InvalidGrowthKey.class)
    ProblemDetail invalidKey(InvalidGrowthKey exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.BAD_REQUEST, "invalid_growth_key", "成长项目无效",
                "请求的成就或任务不存在。", request);
    }

    @ExceptionHandler(AchievementNotClaimable.class)
    ProblemDetail achievementNotClaimable(AchievementNotClaimable exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "achievement_not_claimable", "成就尚未达成",
                "当前没有可领取的成就等级。", request);
    }

    @ExceptionHandler(AchievementAlreadyClaimed.class)
    ProblemDetail achievementAlreadyClaimed(AchievementAlreadyClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "achievement_already_claimed", "成就已经领取",
                "该成就等级已经领取过奖励。", request);
    }

    @ExceptionHandler(TaskAlreadyAccepted.class)
    ProblemDetail taskAlreadyAccepted(TaskAlreadyAccepted exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "task_already_accepted", "任务已经接取",
                "该限时任务已经在进行中。", request);
    }

    @ExceptionHandler(TaskAlreadyClaimed.class)
    ProblemDetail taskAlreadyClaimed(TaskAlreadyClaimed exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "task_already_claimed", "任务奖励已经领取",
                "本期任务奖励已经领取过。", request);
    }

    @ExceptionHandler(TaskNotAccepted.class)
    ProblemDetail taskNotAccepted(TaskNotAccepted exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "task_not_accepted", "任务尚未接取",
                "请先接取限时任务后再领取奖励。", request);
    }

    @ExceptionHandler(TaskNotCompleted.class)
    ProblemDetail taskNotCompleted(TaskNotCompleted exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "task_not_completed", "任务尚未完成",
                "任务进度还未达到领取条件。", request);
    }

    @ExceptionHandler(TaskExpired.class)
    ProblemDetail taskExpired(TaskExpired exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "task_expired", "任务已过期",
                "限时任务已超过截止时间，可以重新接取。", request);
    }
}
