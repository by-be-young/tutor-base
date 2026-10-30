package com.tutorbase.administration.learner;

final class LearnerSubjectNotClaimed extends RuntimeException {
    LearnerSubjectNotClaimed(String subject) {
        super("当前管理员未认领学科「" + subject + "」，不能为学员开通该科目。");
    }
}
