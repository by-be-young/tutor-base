package com.tutorbase.administration.learner;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/learners")
final class AdminLearnerController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final LearnerQuery learnerQuery;

    AdminLearnerController(LearnerQuery learnerQuery) {
        this.learnerQuery = learnerQuery;
    }

    @GetMapping
    LearnerCollectionResponse listLearners(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit) {
        int parsedLimit = parseLimit(limit);
        LearnerQuery.LearnerPage page = learnerQuery.findAfter(LearnerCursor.decode(cursor), parsedLimit);
        return new LearnerCollectionResponse(
                page.items().stream().map(LearnerResponse::from).toList(),
                page.nextAfterId().isPresent() ? LearnerCursor.encode(page.nextAfterId().getAsLong()) : null);
    }

    private static int parseLimit(String value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new InvalidLearnerPageRequest(
                        "limit", "validation_failed", "limit must be between 1 and 100.");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new InvalidLearnerPageRequest(
                    "limit",
                    "validation_failed",
                    "limit must be an integer between 1 and 100.",
                    exception);
        }
    }

    record LearnerCollectionResponse(List<LearnerResponse> items, String nextCursor) {
    }

    record LearnerResponse(long learnerId, String username, List<Long> contentGrantArticleIds) {

        static LearnerResponse from(LearnerQuery.Learner learner) {
            return new LearnerResponse(
                    learner.learnerId(),
                    learner.username(),
                    learner.contentGrantArticleIds());
        }
    }
}
