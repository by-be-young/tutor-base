package com.tutorbase.administration.learner;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only learner directory, ordered strictly by ascending learner id.
 *
 * <p>The returned page contains at most {@code limit} items. {@code nextAfterId} is present only
 * when another page exists and is the exclusive lower bound for the next call.
 */
public interface LearnerQuery {

    LearnerPage findAfter(OptionalLong afterId, int limit);

    Optional<Learner> findById(long learnerId);

    record Learner(long learnerId, String username, List<Long> contentGrantArticleIds) {

        public Learner {
            contentGrantArticleIds = List.copyOf(contentGrantArticleIds);
        }
    }

    record LearnerPage(List<Learner> items, OptionalLong nextAfterId) {

        public LearnerPage {
            items = List.copyOf(items);
        }
    }
}
