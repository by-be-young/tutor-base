package com.tutorbase.administration.learner;

import java.util.List;

public interface LearnerAdministration {

    long create(String username, long creatorAccountId);

    void replaceContentGrants(
            long learnerId, long administratorAccountId, List<Long> articleIds, List<String> enrolledSubjects);

    void claim(long learnerId, long administratorAccountId);

    void release(long learnerId, long administratorAccountId);

    void deleteLearner(long learnerId, long administratorAccountId);
}
