package com.tutorbase.administration.learner;

import java.util.List;

public interface LearnerAdministration {

    long create(String username);

    void replaceContentGrants(long learnerId, List<Long> articleIds);
}
