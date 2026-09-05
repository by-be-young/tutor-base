package com.tutorbase.wrongbook;

import java.time.Instant;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wrong-book")
@Validated
public class WrongBookController {

    private final WrongBookService wrongBook;

    WrongBookController(WrongBookService wrongBook) {
        this.wrongBook = wrongBook;
    }

    @GetMapping
    ResponseEntity<List<EntryResponse>> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return ok(wrongBook.list(requireLearner(principal)).stream().map(EntryResponse::from).toList());
    }

    @PostMapping("/entries")
    ResponseEntity<EntryResponse> collect(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CollectRequest request) {
        EntryResponse response = EntryResponse.from(wrongBook.collectManual(requireLearner(principal),
                request.sourceArticleId(), request.sourceQuestionId(), request.myAnswer()));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(response);
    }

    @PatchMapping("/entries/{entryId}")
    ResponseEntity<EntryResponse> update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Positive long entryId,
            @Valid @RequestBody UpdateRequest request) {
        return ok(EntryResponse.from(wrongBook.update(requireLearner(principal), entryId,
                new WrongBookService.EntryPatch(request.myAnswer(), request.wrongReason(),
                        request.tags(), request.note(), request.mastered()))));
    }

    @DeleteMapping("/entries/{entryId}")
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Positive long entryId) {
        wrongBook.remove(requireLearner(principal), entryId);
        return ResponseEntity.noContent().build();
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearnerContextRequired();
        }
        return principal.learnerId();
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record CollectRequest(@Positive long sourceArticleId,
                          @NotBlank @Size(max = 200) String sourceQuestionId,
                          @Size(max = 50_000) String myAnswer) {
        CollectRequest {
            myAnswer = myAnswer == null ? "" : myAnswer;
        }
    }

    record UpdateRequest(@Size(max = 50_000) String myAnswer,
                         @Size(max = 10_000) String wrongReason,
                         @Size(max = 100) List<@NotBlank @Size(max = 100) String> tags,
                         @Size(max = 20_000) String note,
                         Boolean mastered) {
    }

    record EntryResponse(long id, long learnerId, String correctAnswer, String myAnswer,
                         String wrongReason, List<String> tags, String note, Long sourceArticleId,
                         String sourceQuestionId, boolean manual, boolean mastered, int wrongCount,
                         Instant createdAt, Instant updatedAt) {
        static EntryResponse from(WrongBookService.Entry entry) {
            return new EntryResponse(entry.id(), entry.learnerId(), entry.correctAnswer(), entry.myAnswer(),
                    entry.wrongReason(), entry.tags(), entry.note(), entry.sourceArticleId(),
                    entry.sourceQuestionId(), entry.manual(), entry.mastered(), entry.wrongCount(),
                    entry.createdAt(), entry.updatedAt());
        }
    }

    static final class LearnerContextRequired extends RuntimeException {
    }
}
