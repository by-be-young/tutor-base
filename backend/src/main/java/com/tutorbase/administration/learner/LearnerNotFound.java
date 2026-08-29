package com.tutorbase.administration.learner;

final class LearnerNotFound extends RuntimeException {
    LearnerNotFound(long learnerId) {
        super("未找到学习者（ID：" + learnerId + "）。");
    }
}
