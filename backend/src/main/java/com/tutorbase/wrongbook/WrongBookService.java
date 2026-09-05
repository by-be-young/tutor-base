package com.tutorbase.wrongbook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WrongBookService {

    private final JdbcClient jdbc;

    WrongBookService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<Entry> list(long learnerId) {
        return jdbc.sql("""
                SELECT wrong.id, wrong.student_id, wrong.correct_answer, wrong.my_answer,
                       wrong.wrong_reason, wrong.tags, wrong.note, wrong.source_blog_id,
                       wrong.source_question_id, wrong.is_manual, wrong.mastered,
                       wrong.removed, wrong.wrong_count, wrong.created_at, wrong.updated_at,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM public.article_question_submissions submission
                           WHERE submission.student_id = wrong.student_id
                             AND submission.blog_id = wrong.source_blog_id
                             AND submission.question_id = wrong.source_question_id
                             AND submission.review_status = 'reviewed'
                       ) THEN COALESCE(NULLIF(wrong.correct_answer, ''), answer.answer_text, '')
                       ELSE '' END AS resolved_correct_answer
                FROM public.wrong_questions AS wrong
                LEFT JOIN public.article_answer_keys AS answer
                  ON answer.blog_id = wrong.source_blog_id
                 AND answer.question_id = wrong.source_question_id
                WHERE wrong.student_id = :learnerId AND NOT wrong.removed
                ORDER BY wrong.updated_at DESC, wrong.id DESC
                """)
                .param("learnerId", learnerId)
                .query(Entry::map)
                .list();
    }

    @Transactional
    Entry collectManual(long learnerId, long articleId, String questionId, String myAnswer) {
        Entry existing = findBySourceForUpdate(learnerId, articleId, questionId);
        if (existing != null && !existing.removed()) {
            throw new AlreadyCollected();
        }

        long id;
        if (existing == null) {
            id = jdbc.sql("""
                    INSERT INTO public.wrong_questions
                        (student_id, source_blog_id, source_question_id, my_answer,
                         correct_answer, is_manual, removed, wrong_count)
                    VALUES (:learnerId, :articleId, :questionId, :myAnswer,
                            '', true, false, 1)
                    RETURNING id
                    """)
                    .param("learnerId", learnerId)
                    .param("articleId", articleId)
                    .param("questionId", questionId)
                    .param("myAnswer", myAnswer)
                    .query(Long.class)
                    .single();
        } else {
            id = existing.id();
            jdbc.sql("""
                    UPDATE public.wrong_questions
                    SET removed = false, my_answer = :myAnswer, is_manual = true,
                        wrong_count = wrong_count + 1
                    WHERE id = :id AND student_id = :learnerId
                    """)
                    .param("myAnswer", myAnswer)
                    .param("id", id)
                    .param("learnerId", learnerId)
                    .update();
        }
        return findOwned(learnerId, id);
    }

    @Transactional
    Entry update(long learnerId, long id, EntryPatch patch) {
        Entry existing = findOwnedForUpdate(learnerId, id);
        jdbc.sql("""
                UPDATE public.wrong_questions
                SET my_answer = :myAnswer,
                    wrong_reason = :wrongReason,
                    tags = :tags,
                    note = :note,
                    mastered = :mastered
                WHERE id = :id AND student_id = :learnerId
                """)
                .param("myAnswer", patch.myAnswer() == null ? existing.myAnswer() : patch.myAnswer())
                .param("wrongReason", patch.wrongReason() == null ? existing.wrongReason() : patch.wrongReason())
                .param("tags", patch.tags() == null ? existing.tags().toArray(String[]::new) : patch.tags().toArray(String[]::new))
                .param("note", patch.note() == null ? existing.note() : patch.note())
                .param("mastered", patch.mastered() == null ? existing.mastered() : patch.mastered())
                .param("id", id)
                .param("learnerId", learnerId)
                .update();
        return findOwned(learnerId, id);
    }

    @Transactional
    void remove(long learnerId, long id) {
        Entry existing = findOwnedForUpdate(learnerId, id);
        if (existing.manual()) {
            jdbc.sql("DELETE FROM public.wrong_questions WHERE id = :id AND student_id = :learnerId")
                    .param("id", id).param("learnerId", learnerId).update();
        } else {
            jdbc.sql("UPDATE public.wrong_questions SET removed = true WHERE id = :id AND student_id = :learnerId")
                    .param("id", id).param("learnerId", learnerId).update();
        }
    }

    private Entry findBySourceForUpdate(long learnerId, long articleId, String questionId) {
        return jdbc.sql("""
                SELECT wrong.*, wrong.correct_answer AS resolved_correct_answer
                FROM public.wrong_questions AS wrong
                WHERE student_id = :learnerId AND source_blog_id = :articleId
                  AND source_question_id = :questionId
                FOR UPDATE
                """)
                .param("learnerId", learnerId).param("articleId", articleId).param("questionId", questionId)
                .query(Entry::map).optional().orElse(null);
    }

    private Entry findOwnedForUpdate(long learnerId, long id) {
        return jdbc.sql("""
                SELECT wrong.*, wrong.correct_answer AS resolved_correct_answer
                FROM public.wrong_questions AS wrong
                WHERE id = :id AND student_id = :learnerId
                FOR UPDATE
                """)
                .param("id", id).param("learnerId", learnerId)
                .query(Entry::map).optional().orElseThrow(EntryNotFound::new);
    }

    private Entry findOwned(long learnerId, long id) {
        return jdbc.sql("""
                SELECT wrong.*, CASE WHEN EXISTS (
                    SELECT 1 FROM public.article_question_submissions submission
                    WHERE submission.student_id = wrong.student_id
                      AND submission.blog_id = wrong.source_blog_id
                      AND submission.question_id = wrong.source_question_id
                      AND submission.review_status = 'reviewed'
                ) THEN COALESCE(NULLIF(wrong.correct_answer, ''), answer.answer_text, '')
                ELSE '' END AS resolved_correct_answer
                FROM public.wrong_questions AS wrong
                LEFT JOIN public.article_answer_keys AS answer
                  ON answer.blog_id = wrong.source_blog_id AND answer.question_id = wrong.source_question_id
                WHERE wrong.id = :id AND wrong.student_id = :learnerId
                """)
                .param("id", id).param("learnerId", learnerId)
                .query(Entry::map).optional().orElseThrow(EntryNotFound::new);
    }

    record EntryPatch(String myAnswer, String wrongReason, List<String> tags, String note, Boolean mastered) {
    }

    record Entry(long id, long learnerId, String correctAnswer, String myAnswer, String wrongReason,
                 List<String> tags, String note, Long sourceArticleId, String sourceQuestionId,
                 boolean manual, boolean mastered, boolean removed, int wrongCount,
                 Instant createdAt, Instant updatedAt) {
        static Entry map(ResultSet row, int rowNumber) throws SQLException {
            long sourceArticleIdValue = row.getLong("source_blog_id");
            Long sourceArticleId = row.wasNull() ? null : sourceArticleIdValue;
            return new Entry(row.getLong("id"), row.getLong("student_id"),
                    row.getString("resolved_correct_answer"), row.getString("my_answer"),
                    row.getString("wrong_reason"), List.of((String[]) row.getArray("tags").getArray()),
                    row.getString("note"), sourceArticleId, row.getString("source_question_id"),
                    row.getBoolean("is_manual"), row.getBoolean("mastered"), row.getBoolean("removed"),
                    row.getInt("wrong_count"), row.getTimestamp("created_at").toInstant(),
                    row.getTimestamp("updated_at").toInstant());
        }
    }

    static final class EntryNotFound extends RuntimeException {
    }

    static final class AlreadyCollected extends RuntimeException {
    }
}
