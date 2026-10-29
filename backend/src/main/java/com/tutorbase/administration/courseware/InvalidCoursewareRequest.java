package com.tutorbase.administration.courseware;

final class InvalidCoursewareRequest extends RuntimeException {
    InvalidCoursewareRequest(String message) {
        super(message);
    }
}
