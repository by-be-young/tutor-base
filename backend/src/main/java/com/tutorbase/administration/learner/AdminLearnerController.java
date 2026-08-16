package com.tutorbase.administration.learner;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/learners")
public class AdminLearnerController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final LearnerQuery learnerQuery;
    private final LearnerAdministration learnerAdministration;

    public AdminLearnerController(LearnerQuery learnerQuery, LearnerAdministration learnerAdministration) {
        this.learnerQuery = learnerQuery;
        this.learnerAdministration = learnerAdministration;
    }

    @PostMapping
    ResponseEntity<LearnerResponse> createLearner(@Valid @RequestBody CreateLearnerRequest request) {
        long learnerId = learnerAdministration.create(request.username());
        LearnerResponse response = learnerQuery.findById(learnerId)
                .map(LearnerResponse::from)
                .orElseThrow(() -> new LearnerNotFound(learnerId));
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/learners/" + learnerId)).body(response);
    }

    @PutMapping("/{learnerId}/content-grants")
    LearnerResponse replaceContentGrants(
            @PathVariable @Positive long learnerId,
            @Valid @RequestBody ReplaceContentGrantsRequest request) {
        learnerAdministration.replaceContentGrants(learnerId, request.articleIds());
        return learnerQuery.findById(learnerId)
                .map(LearnerResponse::from)
                .orElseThrow(() -> new LearnerNotFound(learnerId));
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

    record CreateLearnerRequest(@NotBlank @Size(max = 100) String username) {
    }

    record ReplaceContentGrantsRequest(
            @NotNull @Size(max = 5_000)
            List<@NotNull @Positive @Max(Integer.MAX_VALUE) Long> articleIds) {
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
