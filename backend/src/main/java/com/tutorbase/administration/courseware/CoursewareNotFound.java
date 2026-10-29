package com.tutorbase.administration.courseware;

final class CoursewareNotFound extends RuntimeException {
    CoursewareNotFound(String message) {
        super(message);
    }
}
