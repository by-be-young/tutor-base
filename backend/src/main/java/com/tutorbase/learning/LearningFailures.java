package com.tutorbase.learning;

final class LearningFailures {

    private LearningFailures() {
    }

    static final class LearnerContextRequired extends RuntimeException {
    }

    static final class ContentAccessDenied extends RuntimeException {
    }

    static final class SubmissionAlreadyReviewed extends RuntimeException {
    }

    static final class SubmissionNotFound extends RuntimeException {
    }
}
