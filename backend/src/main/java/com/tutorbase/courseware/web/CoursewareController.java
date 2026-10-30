package com.tutorbase.courseware.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.publish.CoursewareArticleInput;
import com.tutorbase.courseware.publish.CoursewarePublisher;
import com.tutorbase.courseware.query.CoursewareQuery;
import com.tutorbase.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 课件托管：学员取清单与正文，老师发布新版本。 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class CoursewareController {

    private final CoursewareQuery queries;
    private final CoursewarePublisher publisher;

    CoursewareController(CoursewareQuery queries, CoursewarePublisher publisher) {
        this.queries = queries;
        this.publisher = publisher;
    }

    @GetMapping("/courseware/manifests")
    ResponseEntity<ManifestCollectionResponse> manifests(@AuthenticationPrincipal AccountPrincipal principal) {
        Long learnerId = principal == null ? null : principal.learnerId();
        if (learnerId == null) {
            throw new CoursewareFailures.Forbidden("当前账号不是学习者");
        }
        List<ManifestResponse> items = queries.manifestsFor(learnerId).stream()
                .map(ManifestResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ManifestCollectionResponse(items));
    }

    @GetMapping("/courseware/articles/{teacherId}/{articleId}/content")
    ResponseEntity<String> content(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable @Positive long teacherId,
            @PathVariable @Positive long articleId) {
        CoursewareQuery.Content content = queries.content(principal, teacherId, articleId);
        return ResponseEntity.ok()
                .eTag(content.contentHash())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .body(content.markdown());
    }

    @PostMapping("/admin/courseware/releases")
    ResponseEntity<ReleaseResponse> publish(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ReleaseRequest request) {
        if (principal == null || !"administrator".equals(principal.role())) {
            throw new CoursewareFailures.Forbidden("只有管理员可以发布课件");
        }
        CoursewarePublisher.Published published = publisher.publish(
                principal.accountId(), request.subject(), request.expectVersion(),
                request.articles().stream().map(ReleaseArticleRequest::toInput).toList());
        return ResponseEntity.created(URI.create("/api/v1/courseware/manifests"))
                .cacheControl(CacheControl.noStore())
                .body(ReleaseResponse.from(published));
    }

    record ReleaseRequest(
            @NotBlank @Size(max = 60) String subject,
            Integer expectVersion,
            @NotEmpty @Size(max = 500) List<@Valid ReleaseArticleRequest> articles) {
    }

    record ReleaseArticleRequest(
            Long articleId,
            Long legacyArticleId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 60) String series,
            @NotBlank @Size(max = 500) String logicalPath,
            @NotBlank @Size(max = 1_048_576) String markdown,
            List<@Valid AnswerRequest> answers) {

        CoursewareArticleInput toInput() {
            return new CoursewareArticleInput(articleId, legacyArticleId, title, series, logicalPath, markdown,
                    answers == null ? List.of() : answers.stream().map(AnswerRequest::toInput).toList());
        }
    }

    record AnswerRequest(
            @NotBlank @Size(max = 40) String questionId,
            @NotNull @Size(max = 4000) String answer,
            boolean autoGrade) {

        CoursewareArticleInput.CoursewareAnswerInput toInput() {
            return new CoursewareArticleInput.CoursewareAnswerInput(questionId, answer, autoGrade);
        }
    }

    record ReleaseResponse(
            long teacherId,
            String subject,
            int version,
            String contentHash,
            int articleCount,
            Instant publishedAt) {

        static ReleaseResponse from(CoursewarePublisher.Published published) {
            return new ReleaseResponse(published.teacherId(), published.subject(), published.version(),
                    published.contentHash(), published.articleCount(), published.publishedAt());
        }
    }

    record ManifestCollectionResponse(List<ManifestResponse> items) {
    }

    record ManifestResponse(
            long teacherId,
            String teacherName,
            String subject,
            int version,
            Instant publishedAt,
            String contentHash,
            String etag,
            List<ManifestArticleResponse> articles) {

        static ManifestResponse from(CoursewareQuery.Manifest manifest) {
            return new ManifestResponse(
                    manifest.teacherId(),
                    manifest.teacherName(),
                    manifest.subject(),
                    manifest.version(),
                    manifest.publishedAt(),
                    manifest.contentHash(),
                    "v" + manifest.version() + "-" + manifest.contentHash(),
                    manifest.articles().stream().map(ManifestArticleResponse::from).toList());
        }
    }

    record ManifestArticleResponse(
            long articleId,
            Long legacyArticleId,
            String title,
            String series,
            String logicalPath,
            String contentHash,
            int questionCount) {

        static ManifestArticleResponse from(CoursewareQuery.Article article) {
            return new ManifestArticleResponse(article.articleId(), article.legacyArticleId(), article.title(),
                    article.series(), article.logicalPath(), article.contentHash(), article.questionCount());
        }
    }

    static HttpStatus statusOf(RuntimeException failure) {
        return failure instanceof CoursewareFailures.NotFound ? HttpStatus.NOT_FOUND
                : failure instanceof CoursewareFailures.Forbidden ? HttpStatus.FORBIDDEN
                : failure instanceof CoursewareFailures.VersionConflict ? HttpStatus.CONFLICT
                : failure instanceof CoursewareFailures.Validation ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
