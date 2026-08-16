package com.tutorbase.learning.access;

import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/content-grants")
final class CurrentContentGrantController {

    private final ContentGrantQuery contentGrantQuery;

    CurrentContentGrantController(ContentGrantQuery contentGrantQuery) {
        this.contentGrantQuery = contentGrantQuery;
    }

    @GetMapping
    ContentGrantResponse current(@AuthenticationPrincipal AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        List<Long> articleIds = contentGrantQuery.findArticleIds(principal.learnerId())
                .orElseThrow(LearnerRecordMissing::new);
        return new ContentGrantResponse(articleIds);
    }

    record ContentGrantResponse(List<Long> articleIds) {
        ContentGrantResponse {
            articleIds = List.copyOf(articleIds);
        }
    }
}
