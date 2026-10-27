package com.tutorbase.administration.learner;

final class AdministratorLearnerProtected extends RuntimeException {
    AdministratorLearnerProtected(long learnerId) {
        super("学习者（ID：" + learnerId + "）关联管理员账户，不能通过学员管理删除。");
    }
}
