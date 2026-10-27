package com.tutorbase.administration.learner;

final class LearnerAlreadyClaimed extends RuntimeException {
    LearnerAlreadyClaimed(long learnerId) {
        super("学习者（ID：" + learnerId + "）已被其他管理员认领。");
    }
}
