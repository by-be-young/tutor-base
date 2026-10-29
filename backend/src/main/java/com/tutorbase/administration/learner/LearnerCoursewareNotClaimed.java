package com.tutorbase.administration.learner;

final class LearnerCoursewareNotClaimed extends RuntimeException {
    LearnerCoursewareNotClaimed(long articleId) {
        super("当前管理员未认领课件（ID：" + articleId + "），无法授权该课件。");
    }
}
