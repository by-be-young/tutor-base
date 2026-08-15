package com.tutorbase.administration.learner;

final class InvalidLearnerPageRequest extends RuntimeException {

    private final String field;
    private final String code;

    InvalidLearnerPageRequest(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    InvalidLearnerPageRequest(String field, String code, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.code = code;
    }

    String field() {
        return field;
    }

    String code() {
        return code;
    }
}
