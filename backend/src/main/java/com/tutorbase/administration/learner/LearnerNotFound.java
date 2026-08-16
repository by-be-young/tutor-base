package com.tutorbase.administration.learner;

final class LearnerNotFound extends RuntimeException {
    LearnerNotFound(long learnerId) {
        super("Learner " + learnerId + " was not found.");
    }
}
