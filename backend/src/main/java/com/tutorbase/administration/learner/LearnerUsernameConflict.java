package com.tutorbase.administration.learner;

final class LearnerUsernameConflict extends RuntimeException {
    LearnerUsernameConflict(String username, Throwable cause) {
        super("A learner account already uses username " + username + ".", cause);
    }
}
