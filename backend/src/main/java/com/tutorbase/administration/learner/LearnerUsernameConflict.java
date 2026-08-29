package com.tutorbase.administration.learner;

final class LearnerUsernameConflict extends RuntimeException {
    LearnerUsernameConflict(String username, Throwable cause) {
        super("已有学习者账户使用用户名 " + username + "。", cause);
    }
}
