package com.tutorbase.administration.learner;

final class LearnerNotClaimed extends RuntimeException {
    LearnerNotClaimed(long learnerId) {
        super("学习者（ID：" + learnerId + "）尚未由当前管理员认领，不能执行该操作。");
    }
}
