package com.tutorbase.administration.learner;

final class SpecialTestLearnerProtected extends RuntimeException {
    SpecialTestLearnerProtected(long learnerId) {
        super("特殊测试学员（ID：" + learnerId + "）受保护，不能删除。");
    }
}
