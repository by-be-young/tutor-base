SET lock_timeout = '5s';
SET statement_timeout = '30s';

ALTER TABLE public.student
    ALTER COLUMN permissions SET NOT NULL;

ALTER TABLE public.wrong_questions
    ADD CONSTRAINT wrong_questions_student_id_fkey
        FOREIGN KEY (student_id) REFERENCES public.student (id) ON DELETE CASCADE,
    ADD CONSTRAINT wrong_questions_wrong_count_check
        CHECK (wrong_count >= 1);

ALTER TABLE public.article_question_submissions
    ADD CONSTRAINT article_question_submissions_review_state_check
        CHECK (
            (review_status = 'pending' AND review_result IS NULL AND reviewed_at IS NULL)
            OR
            (review_status = 'reviewed' AND review_result IS NOT NULL AND reviewed_at IS NOT NULL)
        );

CREATE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$function$;

CREATE TRIGGER article_answer_keys_set_updated_at
    BEFORE UPDATE ON public.article_answer_keys
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER article_question_submissions_set_updated_at
    BEFORE UPDATE ON public.article_question_submissions
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER wrong_questions_set_updated_at
    BEFORE UPDATE ON public.wrong_questions
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
