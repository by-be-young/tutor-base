-- One-time reconciliation for the verified Supabase schema before Flyway baseline V3.
-- Take and verify a restorable backup first. Run during a schema maintenance window.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $preconditions$
BEGIN
    IF EXISTS (SELECT 1 FROM public.student WHERE permissions IS NULL) THEN
        RAISE EXCEPTION 'Cannot reconcile: student.permissions contains null values';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.article_question_submissions
        WHERE (review_status = 'pending' AND (review_result IS NOT NULL OR reviewed_at IS NOT NULL))
           OR (review_status = 'reviewed' AND (review_result IS NULL OR reviewed_at IS NULL))
    ) THEN
        RAISE EXCEPTION 'Cannot reconcile: submission review state is inconsistent';
    END IF;
    IF EXISTS (SELECT 1 FROM public.wrong_questions WHERE wrong_count < 1) THEN
        RAISE EXCEPTION 'Cannot reconcile: wrong_questions.wrong_count is below one';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.wrong_questions AS wrong_question
        LEFT JOIN public.student AS student ON student.id = wrong_question.student_id
        WHERE student.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot reconcile: wrong_questions contains an orphan student reference';
    END IF;
END
$preconditions$;

ALTER TABLE public.student
    ALTER COLUMN permissions SET NOT NULL;

DO $constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.article_question_submissions'::regclass
          AND conname = 'article_question_submissions_review_state_check'
    ) THEN
        ALTER TABLE public.article_question_submissions
            ADD CONSTRAINT article_question_submissions_review_state_check
            CHECK (
                (review_status = 'pending' AND review_result IS NULL AND reviewed_at IS NULL)
                OR
                (review_status = 'reviewed' AND review_result IS NOT NULL AND reviewed_at IS NOT NULL)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.wrong_questions'::regclass
          AND conname = 'wrong_questions_student_id_fkey'
    ) THEN
        ALTER TABLE public.wrong_questions
            ADD CONSTRAINT wrong_questions_student_id_fkey
            FOREIGN KEY (student_id) REFERENCES public.student (id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.wrong_questions'::regclass
          AND conname = 'wrong_questions_wrong_count_check'
    ) THEN
        ALTER TABLE public.wrong_questions
            ADD CONSTRAINT wrong_questions_wrong_count_check CHECK (wrong_count >= 1);
    END IF;
END
$constraints$;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$function$;

DO $triggers$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.article_answer_keys'::regclass
          AND tgname = 'article_answer_keys_set_updated_at'
          AND NOT tgisinternal
    ) THEN
        EXECUTE 'CREATE TRIGGER article_answer_keys_set_updated_at
                 BEFORE UPDATE ON public.article_answer_keys
                 FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.article_question_submissions'::regclass
          AND tgname = 'article_question_submissions_set_updated_at'
          AND NOT tgisinternal
    ) THEN
        EXECUTE 'CREATE TRIGGER article_question_submissions_set_updated_at
                 BEFORE UPDATE ON public.article_question_submissions
                 FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.wrong_questions'::regclass
          AND tgname = 'wrong_questions_set_updated_at'
          AND NOT tgisinternal
    ) THEN
        EXECUTE 'CREATE TRIGGER wrong_questions_set_updated_at
                 BEFORE UPDATE ON public.wrong_questions
                 FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()';
    END IF;
END
$triggers$;

COMMIT;
