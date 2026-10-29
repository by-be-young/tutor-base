package com.tutorbase.administration.analytics;

/** 请求的 learnerId 在 public.student 中不存在。 */
final class AnalyticsLearnerNotFound extends RuntimeException {
    AnalyticsLearnerNotFound(long learnerId) {
        super("未找到学习者（ID：" + learnerId + "）。");
    }
}
