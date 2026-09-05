-- Run only after the HTTP-backend frontend has been deployed and smoke-tested.
-- Supabase-managed roles may be absent in local PostgreSQL, so each revoke is guarded.
BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $revoke$
DECLARE
    role_name text;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON TABLE %s FROM %I',
                'public.student, public.account, public.account_activation, public.account_session, '
                'public.article_answer_keys, public.article_question_submissions, '
                'public.wrong_questions, public.daily_check_in, public.user_points, '
                'public.task_claims, public.card_collection',
                role_name);
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON SEQUENCE %s FROM %I',
                'public.student_id_seq, public.account_id_seq, public.account_activation_id_seq, '
                'public.account_session_id_seq, public.daily_check_in_id_seq, '
                'public.article_answer_keys_id_seq, public.article_question_submissions_id_seq, '
                'public.wrong_questions_id_seq, public.task_claims_id_seq, public.card_collection_id_seq',
                role_name);
        END IF;
    END LOOP;
END
$revoke$;

COMMIT;
