package com.tutorbase.administration.analytics;

final class AnalyticsLearnerNotClaimed extends RuntimeException {
    AnalyticsLearnerNotClaimed(long learnerId) {
        super("学习者（ID：" + learnerId + "）尚未由当前管理员认领，不能查看详情。");
    }
}
