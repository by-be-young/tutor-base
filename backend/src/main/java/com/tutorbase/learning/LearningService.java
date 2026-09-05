package com.tutorbase.learning;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LearningService {

    private final JdbcClient jdbc;

    LearningService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    ArticleStudyState learnerState(long learnerId, long articleId) {
        requireContentGrant(learnerId, articleId);
        return combine(articleId, findAnswerKeys(articleId), findSubmissions(articleId, learnerId), false);
    }

    @Transactional(readOnly = true)
    ArticleStudyState administratorState(long learnerId, long articleId) {
        requireLearnerExists(learnerId);
        return combine(articleId, findAnswerKeys(articleId), findSubmissions(articleId, learnerId), true);
    }

    @Transactional(readOnly = true)
    List<AnswerKey> answerKeys(long articleId) {
        return findAnswerKeys(articleId);
    }

    @Transactional(readOnly = true)
    List<Long> answerKeyArticleIds() {
        return jdbc.sql("SELECT DISTINCT blog_id FROM public.article_answer_keys ORDER BY blog_id")
                .query(Long.class).list();
    }

    @Transactional(readOnly = true)
    List<ArticleProgress> learnerProgress(long learnerId) {
        return jdbc.sql("""
                SELECT answer.blog_id,
                       count(*) AS total_questions,
                       count(submission.id) AS submitted_questions,
                       count(*) FILTER (WHERE submission.review_status = 'pending') AS pending_reviews
                FROM public.student AS learner
                JOIN public.article_answer_keys AS answer
                  ON answer.blog_id = ANY (learner.permissions::bigint[])
                LEFT JOIN public.article_question_submissions AS submission
                  ON submission.blog_id = answer.blog_id
                 AND submission.question_id = answer.question_id
                 AND submission.student_id = learner.id
                WHERE learner.id = :learnerId
                GROUP BY answer.blog_id
                ORDER BY answer.blog_id
                """)
                .param("learnerId", learnerId)
                .query(ArticleProgress::map).list();
    }

    @Transactional(readOnly = true)
    List<ArticleProgress> administratorProgress(long learnerId) {
        requireLearnerExists(learnerId);
        return jdbc.sql("""
                SELECT answer.blog_id,
                       count(*) AS total_questions,
                       count(submission.id) AS submitted_questions,
                       count(*) FILTER (WHERE submission.review_status = 'pending') AS pending_reviews
                FROM public.article_answer_keys AS answer
                LEFT JOIN public.article_question_submissions AS submission
                  ON submission.blog_id = answer.blog_id
                 AND submission.question_id = answer.question_id
                 AND submission.student_id = :learnerId
                GROUP BY answer.blog_id
                ORDER BY answer.blog_id
                """)
                .param("learnerId", learnerId)
                .query(ArticleProgress::map).list();
    }

    @Transactional
    List<AnswerKey> replaceAnswerKeys(long articleId, List<AnswerKeyInput> inputs) {
        jdbc.sql("DELETE FROM public.article_answer_keys WHERE blog_id = :articleId")
                .param("articleId", articleId)
                .update();
        for (AnswerKeyInput input : inputs) {
            jdbc.sql("""
                    INSERT INTO public.article_answer_keys
                        (blog_id, question_id, answer_text, auto_grade)
                    VALUES (:articleId, :questionId, :answerText, :autoGrade)
                    ON CONFLICT (blog_id, question_id) DO UPDATE
                        SET answer_text = EXCLUDED.answer_text,
                            auto_grade = EXCLUDED.auto_grade
                    """)
                    .param("articleId", articleId)
                    .param("questionId", input.questionId())
                    .param("answerText", input.answerText())
                    .param("autoGrade", input.autoGrade())
                    .update();
        }
        return findAnswerKeys(articleId);
    }

    @Transactional
    Submission submit(long learnerId, long articleId, String questionId, String answerText) {
        requireContentGrant(learnerId, articleId);
        String currentState = jdbc.sql("""
                SELECT review_status
                FROM public.article_question_submissions
                WHERE blog_id = :articleId AND student_id = :learnerId AND question_id = :questionId
                FOR UPDATE
                """)
                .param("articleId", articleId)
                .param("learnerId", learnerId)
                .param("questionId", questionId)
                .query(String.class)
                .optional()
                .orElse(null);
        if ("reviewed".equals(currentState)) {
            throw new LearningFailures.SubmissionAlreadyReviewed();
        }

        AnswerKey key = findAnswerKey(articleId, questionId);
        boolean autoGrade = key != null && key.autoGrade() && !key.answerText().isBlank();
        String reviewStatus = autoGrade ? "reviewed" : "pending";
        String reviewResult = autoGrade ? AnswerGradingPolicy.grade(answerText, key.answerText()) : null;

        Submission submission = jdbc.sql("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text,
                     review_status, review_result, submitted_at, reviewed_at)
                VALUES (:articleId, :learnerId, :questionId, :answerText,
                        :reviewStatus, :reviewResult, now(),
                        CASE WHEN :reviewStatus = 'reviewed' THEN now() ELSE NULL END)
                ON CONFLICT (blog_id, student_id, question_id) DO UPDATE
                    SET answer_text = EXCLUDED.answer_text,
                        review_status = EXCLUDED.review_status,
                        review_result = EXCLUDED.review_result,
                        submitted_at = EXCLUDED.submitted_at,
                        reviewed_at = EXCLUDED.reviewed_at
                RETURNING id, blog_id, student_id, question_id, answer_text,
                          review_status, review_result, submitted_at, reviewed_at
                """)
                .param("articleId", articleId)
                .param("learnerId", learnerId)
                .param("questionId", questionId)
                .param("answerText", answerText)
                .param("reviewStatus", reviewStatus)
                .param("reviewResult", reviewResult)
                .query(Submission::map)
                .single();

        if ("wrong".equals(reviewResult)) {
            collectAutomaticWrongQuestion(learnerId, articleId, questionId, answerText, key.answerText());
        }
        return submission;
    }

    @Transactional
    Submission review(long learnerId, long articleId, String questionId, String reviewResult) {
        Submission existing = jdbc.sql("""
                SELECT id, blog_id, student_id, question_id, answer_text,
                       review_status, review_result, submitted_at, reviewed_at
                FROM public.article_question_submissions
                WHERE blog_id = :articleId AND student_id = :learnerId AND question_id = :questionId
                FOR UPDATE
                """)
                .param("articleId", articleId)
                .param("learnerId", learnerId)
                .param("questionId", questionId)
                .query(Submission::map)
                .optional()
                .orElseThrow(LearningFailures.SubmissionNotFound::new);

        Submission reviewed = jdbc.sql("""
                UPDATE public.article_question_submissions
                SET review_status = 'reviewed', review_result = :reviewResult, reviewed_at = now()
                WHERE id = :submissionId
                RETURNING id, blog_id, student_id, question_id, answer_text,
                          review_status, review_result, submitted_at, reviewed_at
                """)
                .param("reviewResult", reviewResult)
                .param("submissionId", existing.id())
                .query(Submission::map)
                .single();

        if ("wrong".equals(reviewResult)) {
            AnswerKey key = findAnswerKey(articleId, questionId);
            collectAutomaticWrongQuestion(learnerId, articleId, questionId, existing.answerText(),
                    key == null ? "" : key.answerText());
        }
        return reviewed;
    }

    private void requireContentGrant(long learnerId, long articleId) {
        boolean granted = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public.student
                    WHERE id = :learnerId
                      AND CAST(:articleId AS bigint) = ANY (permissions::bigint[])
                )
                """)
                .param("learnerId", learnerId)
                .param("articleId", articleId)
                .query(Boolean.class)
                .single();
        if (!granted) {
            throw new LearningFailures.ContentAccessDenied();
        }
    }

    private void requireLearnerExists(long learnerId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM public.student WHERE id = :learnerId)")
                .param("learnerId", learnerId)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new LearningFailures.SubmissionNotFound();
        }
    }

    private List<AnswerKey> findAnswerKeys(long articleId) {
        return jdbc.sql("""
                SELECT question_id, answer_text, auto_grade, updated_at
                FROM public.article_answer_keys
                WHERE blog_id = :articleId
                ORDER BY question_id
                """)
                .param("articleId", articleId)
                .query(AnswerKey::map)
                .list();
    }

    private AnswerKey findAnswerKey(long articleId, String questionId) {
        return jdbc.sql("""
                SELECT question_id, answer_text, auto_grade, updated_at
                FROM public.article_answer_keys
                WHERE blog_id = :articleId AND question_id = :questionId
                """)
                .param("articleId", articleId)
                .param("questionId", questionId)
                .query(AnswerKey::map)
                .optional()
                .orElse(null);
    }

    private List<Submission> findSubmissions(long articleId, long learnerId) {
        return jdbc.sql("""
                SELECT id, blog_id, student_id, question_id, answer_text,
                       review_status, review_result, submitted_at, reviewed_at
                FROM public.article_question_submissions
                WHERE blog_id = :articleId AND student_id = :learnerId
                ORDER BY question_id
                """)
                .param("articleId", articleId)
                .param("learnerId", learnerId)
                .query(Submission::map)
                .list();
    }

    private ArticleStudyState combine(long articleId, List<AnswerKey> keys, List<Submission> submissions,
                                      boolean revealAllAnswers) {
        Map<String, QuestionState> states = new LinkedHashMap<>();
        for (AnswerKey key : keys) {
            states.put(key.questionId(), new QuestionState(
                    key.questionId(), revealAllAnswers ? key.answerText() : null,
                    revealAllAnswers ? key.autoGrade() : null, null));
        }
        for (Submission submission : submissions) {
            QuestionState state = states.get(submission.questionId());
            AnswerKey key = keys.stream()
                    .filter(candidate -> candidate.questionId().equals(submission.questionId()))
                    .findFirst().orElse(null);
            boolean reveal = revealAllAnswers || "reviewed".equals(submission.reviewStatus());
            states.put(submission.questionId(), new QuestionState(
                    submission.questionId(), reveal && key != null ? key.answerText() : null,
                    revealAllAnswers && key != null ? key.autoGrade() : null, submission));
        }
        return new ArticleStudyState(articleId, List.copyOf(states.values()));
    }

    private void collectAutomaticWrongQuestion(long learnerId, long articleId, String questionId,
                                               String myAnswer, String correctAnswer) {
        jdbc.sql("""
                INSERT INTO public.wrong_questions
                    (student_id, source_blog_id, source_question_id, my_answer,
                     correct_answer, is_manual, removed, wrong_count)
                VALUES (:learnerId, :articleId, :questionId, :myAnswer,
                        :correctAnswer, false, false, 1)
                ON CONFLICT (student_id, source_blog_id, source_question_id) DO UPDATE
                    SET my_answer = EXCLUDED.my_answer,
                        correct_answer = EXCLUDED.correct_answer,
                        is_manual = false,
                        removed = false,
                        wrong_count = wrong_questions.wrong_count + 1
                """)
                .param("learnerId", learnerId)
                .param("articleId", articleId)
                .param("questionId", questionId)
                .param("myAnswer", myAnswer)
                .param("correctAnswer", correctAnswer)
                .update();
    }

    record AnswerKeyInput(String questionId, String answerText, boolean autoGrade) {
    }

    record AnswerKey(String questionId, String answerText, boolean autoGrade, Instant updatedAt) {
        static AnswerKey map(ResultSet row, int rowNumber) throws SQLException {
            return new AnswerKey(row.getString("question_id"), row.getString("answer_text"),
                    row.getBoolean("auto_grade"), row.getTimestamp("updated_at").toInstant());
        }
    }

    record Submission(long id, long articleId, long learnerId, String questionId, String answerText,
                      String reviewStatus, String reviewResult, Instant submittedAt, Instant reviewedAt) {
        static Submission map(ResultSet row, int rowNumber) throws SQLException {
            var reviewedAt = row.getTimestamp("reviewed_at");
            return new Submission(row.getLong("id"), row.getLong("blog_id"), row.getLong("student_id"),
                    row.getString("question_id"), row.getString("answer_text"),
                    row.getString("review_status"), row.getString("review_result"),
                    row.getTimestamp("submitted_at").toInstant(),
                    reviewedAt == null ? null : reviewedAt.toInstant());
        }
    }

    record QuestionState(String questionId, String answerText, Boolean autoGrade, Submission submission) {
    }

    record ArticleStudyState(long articleId, List<QuestionState> questions) {
    }

    record ArticleProgress(long articleId, int totalQuestions, int submittedQuestions, int pendingReviews) {
        static ArticleProgress map(ResultSet row, int rowNumber) throws SQLException {
            return new ArticleProgress(row.getLong("blog_id"), row.getInt("total_questions"),
                    row.getInt("submitted_questions"), row.getInt("pending_reviews"));
        }
    }
}
