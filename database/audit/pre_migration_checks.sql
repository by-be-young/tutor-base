-- Read-only checks to run against the linked Supabase production database
-- before creating stricter constraints or copying data to cloud PostgreSQL.
-- Every anomaly_count should be zero unless the accompanying note says otherwise.

BEGIN TRANSACTION READ ONLY;

SELECT 'student.username duplicate' AS check_name, count(*) AS anomaly_count
FROM (
    SELECT username
    FROM public.student
    GROUP BY username
    HAVING count(*) > 1
) AS duplicates;

SELECT 'student normalized username duplicate' AS check_name, count(*) AS anomaly_count
FROM (
    SELECT lower(btrim(username))
    FROM public.student
    GROUP BY lower(btrim(username))
    HAVING count(*) > 1
) AS duplicates;

SELECT 'student blank normalized username' AS check_name, count(*) AS anomaly_count
FROM public.student
WHERE btrim(username) = '';

SELECT 'student.permissions is null' AS check_name, count(*) AS anomaly_count
FROM public.student
WHERE permissions IS NULL;

SELECT 'answer key business key duplicate' AS check_name, count(*) AS anomaly_count
FROM (
    SELECT blog_id, question_id
    FROM public.article_answer_keys
    GROUP BY blog_id, question_id
    HAVING count(*) > 1
) AS duplicates;

SELECT 'submission business key duplicate' AS check_name, count(*) AS anomaly_count
FROM (
    SELECT blog_id, student_id, question_id
    FROM public.article_question_submissions
    GROUP BY blog_id, student_id, question_id
    HAVING count(*) > 1
) AS duplicates;

SELECT 'submission orphan student' AS check_name, count(*) AS anomaly_count
FROM public.article_question_submissions AS submission
LEFT JOIN public.student AS student ON student.id = submission.student_id
WHERE student.id IS NULL;

SELECT 'wrong question orphan student' AS check_name, count(*) AS anomaly_count
FROM public.wrong_questions AS wrong_question
LEFT JOIN public.student AS student ON student.id = wrong_question.student_id
WHERE student.id IS NULL;

SELECT 'submission invalid review status/result' AS check_name, count(*) AS anomaly_count
FROM public.article_question_submissions
WHERE review_status NOT IN ('pending', 'reviewed')
   OR review_result IS NOT NULL
      AND review_result NOT IN ('correct', 'partial', 'wrong');

SELECT 'pending submission has review data' AS check_name, count(*) AS anomaly_count
FROM public.article_question_submissions
WHERE review_status = 'pending'
  AND (review_result IS NOT NULL OR reviewed_at IS NOT NULL);

SELECT 'reviewed submission lacks review data' AS check_name, count(*) AS anomaly_count
FROM public.article_question_submissions
WHERE review_status = 'reviewed'
  AND (review_result IS NULL OR reviewed_at IS NULL);

SELECT 'wrong question count below one' AS check_name, count(*) AS anomaly_count
FROM public.wrong_questions
WHERE wrong_count < 1;

-- Informational: text question identifiers are intentional. This shows how many
-- existing identifiers would be damaged by an attempted integer conversion.
SELECT 'non-numeric question identifiers (informational)' AS check_name,
       count(*) AS anomaly_count
FROM (
    SELECT question_id
    FROM public.article_answer_keys
    UNION
    SELECT question_id
    FROM public.article_question_submissions
    UNION
    SELECT source_question_id
    FROM public.wrong_questions
) AS identifiers
WHERE question_id IS NOT NULL
  AND question_id !~ '^[0-9]+$';

-- Record these values for the migration report and sequence reset verification.
SELECT 'student' AS table_name, count(*) AS row_count, coalesce(max(id), 0) AS max_id
FROM public.student
UNION ALL
SELECT 'article_answer_keys', count(*), coalesce(max(id), 0)
FROM public.article_answer_keys
UNION ALL
SELECT 'article_question_submissions', count(*), coalesce(max(id), 0)
FROM public.article_question_submissions
UNION ALL
SELECT 'wrong_questions', count(*), coalesce(max(id), 0)
FROM public.wrong_questions;

-- Supabase SQL Editor commonly foregrounds only the last result set. Return a
-- consolidated JSON report last so it can be copied without switching tabs.
SELECT jsonb_build_object(
    'checks', jsonb_build_array(
        jsonb_build_object(
            'check_name', 'student.username duplicate',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT username FROM public.student
                    GROUP BY username HAVING count(*) > 1
                ) AS duplicates
            )
        ),
        jsonb_build_object(
            'check_name', 'student normalized username duplicate',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT lower(btrim(username)) FROM public.student
                    GROUP BY lower(btrim(username)) HAVING count(*) > 1
                ) AS duplicates
            )
        ),
        jsonb_build_object(
            'check_name', 'student blank normalized username',
            'anomaly_count', (
                SELECT count(*) FROM public.student WHERE btrim(username) = ''
            )
        ),
        jsonb_build_object(
            'check_name', 'student.permissions is null',
            'anomaly_count', (
                SELECT count(*) FROM public.student WHERE permissions IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'answer key business key duplicate',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT blog_id, question_id FROM public.article_answer_keys
                    GROUP BY blog_id, question_id HAVING count(*) > 1
                ) AS duplicates
            )
        ),
        jsonb_build_object(
            'check_name', 'submission business key duplicate',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT blog_id, student_id, question_id
                    FROM public.article_question_submissions
                    GROUP BY blog_id, student_id, question_id HAVING count(*) > 1
                ) AS duplicates
            )
        ),
        jsonb_build_object(
            'check_name', 'submission orphan student',
            'anomaly_count', (
                SELECT count(*)
                FROM public.article_question_submissions AS submission
                LEFT JOIN public.student AS student ON student.id = submission.student_id
                WHERE student.id IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'wrong question orphan student',
            'anomaly_count', (
                SELECT count(*)
                FROM public.wrong_questions AS wrong_question
                LEFT JOIN public.student AS student ON student.id = wrong_question.student_id
                WHERE student.id IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'invalid review status/result',
            'anomaly_count', (
                SELECT count(*)
                FROM public.article_question_submissions
                WHERE review_status NOT IN ('pending', 'reviewed')
                   OR review_result IS NOT NULL
                      AND review_result NOT IN ('correct', 'partial', 'wrong')
            )
        ),
        jsonb_build_object(
            'check_name', 'pending submission has review data',
            'anomaly_count', (
                SELECT count(*)
                FROM public.article_question_submissions
                WHERE review_status = 'pending'
                  AND (review_result IS NOT NULL OR reviewed_at IS NOT NULL)
            )
        ),
        jsonb_build_object(
            'check_name', 'reviewed submission lacks review data',
            'anomaly_count', (
                SELECT count(*)
                FROM public.article_question_submissions
                WHERE review_status = 'reviewed'
                  AND (review_result IS NULL OR reviewed_at IS NULL)
            )
        ),
        jsonb_build_object(
            'check_name', 'wrong question count below one',
            'anomaly_count', (
                SELECT count(*) FROM public.wrong_questions WHERE wrong_count < 1
            )
        ),
        jsonb_build_object(
            'check_name', 'non-numeric question identifiers (informational)',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT question_id FROM public.article_answer_keys
                    UNION
                    SELECT question_id FROM public.article_question_submissions
                    UNION
                    SELECT source_question_id FROM public.wrong_questions
                ) AS identifiers
                WHERE question_id IS NOT NULL
                  AND question_id !~ '^[0-9]+$'
            )
        )
    ),
    'inventory', (
        SELECT jsonb_agg(to_jsonb(inventory) ORDER BY inventory.table_name)
        FROM (
            SELECT 'student' AS table_name, count(*) AS row_count,
                   coalesce(max(id), 0) AS max_id
            FROM public.student
            UNION ALL
            SELECT 'article_answer_keys', count(*), coalesce(max(id), 0)
            FROM public.article_answer_keys
            UNION ALL
            SELECT 'article_question_submissions', count(*), coalesce(max(id), 0)
            FROM public.article_question_submissions
            UNION ALL
            SELECT 'wrong_questions', count(*), coalesce(max(id), 0)
            FROM public.wrong_questions
        ) AS inventory
    )
) AS migration_report;

ROLLBACK;
