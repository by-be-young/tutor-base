package com.tutorbase.learning;

import java.time.Instant;
import java.util.List;

import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class LearningController {

    private final LearningService learning;

    LearningController(LearningService learning) {
        this.learning = learning;
    }

    @GetMapping("/articles/{articleId}/study-state")
    ResponseEntity<ArticleStudyStateResponse> learnerState(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Positive long articleId) {
        return noStore(ArticleStudyStateResponse.from(
                learning.learnerState(requireLearner(principal), articleId)));
    }

    @PutMapping("/articles/{articleId}/submissions/{questionId}")
    ResponseEntity<SubmissionResponse> submit(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Positive long articleId,
            @PathVariable @NotBlank @Size(max = 200) String questionId,
            @Valid @RequestBody SaveSubmissionRequest request) {
        return noStore(SubmissionResponse.from(
                learning.submit(requireLearner(principal), articleId, questionId, request.answerText())));
    }

    @GetMapping("/admin/articles/{articleId}/answer-keys")
    ResponseEntity<List<AnswerKeyResponse>> answerKeys(@PathVariable @Positive long articleId) {
        return noStore(learning.answerKeys(articleId).stream().map(AnswerKeyResponse::from).toList());
    }

    @GetMapping("/admin/articles/answer-key-article-ids")
    ResponseEntity<List<Long>> answerKeyArticleIds() {
        return noStore(learning.answerKeyArticleIds());
    }

    @GetMapping("/articles/study-progress")
    ResponseEntity<List<ArticleProgressResponse>> learnerProgress(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return noStore(learning.learnerProgress(requireLearner(principal)).stream()
                .map(ArticleProgressResponse::from).toList());
    }

    @GetMapping("/admin/learners/{learnerId}/study-progress")
    ResponseEntity<List<ArticleProgressResponse>> administratorProgress(
            @PathVariable @Positive long learnerId) {
        return noStore(learning.administratorProgress(learnerId).stream()
                .map(ArticleProgressResponse::from).toList());
    }

    @PutMapping("/admin/articles/{articleId}/answer-keys")
    ResponseEntity<List<AnswerKeyResponse>> replaceAnswerKeys(
            @PathVariable @Positive long articleId,
            @Valid @RequestBody ReplaceAnswerKeysRequest request) {
        List<LearningService.AnswerKeyInput> inputs = request.items().stream()
                .map(item -> new LearningService.AnswerKeyInput(
                        item.questionId(), item.answerText(), item.autoGrade()))
                .toList();
        return noStore(learning.replaceAnswerKeys(articleId, inputs).stream()
                .map(AnswerKeyResponse::from).toList());
    }

    @GetMapping("/admin/articles/{articleId}/study-state")
    ResponseEntity<ArticleStudyStateResponse> administratorState(
            @PathVariable @Positive long articleId,
            @RequestParam @Positive long learnerId) {
        return noStore(ArticleStudyStateResponse.from(learning.administratorState(learnerId, articleId)));
    }

    @PutMapping("/admin/articles/{articleId}/learners/{learnerId}/submissions/{questionId}/review")
    ResponseEntity<SubmissionResponse> review(
            @PathVariable @Positive long articleId,
            @PathVariable @Positive long learnerId,
            @PathVariable @NotBlank @Size(max = 200) String questionId,
            @Valid @RequestBody ReviewSubmissionRequest request) {
        return noStore(SubmissionResponse.from(
                learning.review(learnerId, articleId, questionId, request.result().name())));
    }

    private static long requireLearner(AccountPrincipal principal) {
        if (principal == null || principal.learnerId() == null) {
            throw new LearningFailures.LearnerContextRequired();
        }
        return principal.learnerId();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record SaveSubmissionRequest(@NotNull @Size(max = 50_000) String answerText) {
    }

    record ReviewSubmissionRequest(@NotNull ReviewResult result) {
    }

    enum ReviewResult {
        correct, partial, wrong
    }

    record ReplaceAnswerKeysRequest(
            @NotEmpty @Size(max = 1_000) List<@Valid AnswerKeyInputRequest> items) {
    }

    record AnswerKeyInputRequest(
            @NotBlank @Size(max = 200) String questionId,
            @NotNull @Size(max = 50_000) String answerText,
            boolean autoGrade) {
    }

    record AnswerKeyResponse(String questionId, String answerText, boolean autoGrade, Instant updatedAt) {
        static AnswerKeyResponse from(LearningService.AnswerKey key) {
            return new AnswerKeyResponse(key.questionId(), key.answerText(), key.autoGrade(), key.updatedAt());
        }
    }

    record SubmissionResponse(long id, long articleId, long learnerId, String questionId, String answerText,
                              String reviewStatus, String reviewResult, Instant submittedAt, Instant reviewedAt) {
        static SubmissionResponse from(LearningService.Submission submission) {
            return new SubmissionResponse(submission.id(), submission.articleId(), submission.learnerId(),
                    submission.questionId(), submission.answerText(), submission.reviewStatus(),
                    submission.reviewResult(), submission.submittedAt(), submission.reviewedAt());
        }
    }

    record QuestionStateResponse(String questionId, String answerText, Boolean autoGrade,
                                 SubmissionResponse submission) {
        static QuestionStateResponse from(LearningService.QuestionState state) {
            return new QuestionStateResponse(state.questionId(), state.answerText(), state.autoGrade(),
                    state.submission() == null ? null : SubmissionResponse.from(state.submission()));
        }
    }

    record ArticleStudyStateResponse(long articleId, List<QuestionStateResponse> questions) {
        static ArticleStudyStateResponse from(LearningService.ArticleStudyState state) {
            return new ArticleStudyStateResponse(state.articleId(),
                    state.questions().stream().map(QuestionStateResponse::from).toList());
        }
    }

    record ArticleProgressResponse(long articleId, int totalQuestions, int submittedQuestions,
                                   int pendingReviews) {
        static ArticleProgressResponse from(LearningService.ArticleProgress progress) {
            return new ArticleProgressResponse(progress.articleId(), progress.totalQuestions(),
                    progress.submittedQuestions(), progress.pendingReviews());
        }
    }
}
