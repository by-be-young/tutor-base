-- Provision the least-privilege login used by the Java backend.
-- Run as the Supabase postgres role. This script intentionally does not set a password.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $roles$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        CREATE ROLE tutor_base_runtime
            NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_app') THEN
        CREATE ROLE tutor_base_app
            LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS
            CONNECTION LIMIT 5;
    END IF;
END
$roles$;

DO $safe_existing_roles$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname IN ('tutor_base_runtime', 'tutor_base_app')
          AND (rolsuper OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'Refusing to provision: an existing tutor-base role has privileged attributes';
    END IF;
END
$safe_existing_roles$;

ALTER ROLE tutor_base_runtime
    NOLOGIN NOCREATEDB NOCREATEROLE INHERIT;
ALTER ROLE tutor_base_app
    LOGIN NOCREATEDB NOCREATEROLE INHERIT
    CONNECTION LIMIT 5;
ALTER ROLE tutor_base_app SET statement_timeout = '15s';
ALTER ROLE tutor_base_app SET lock_timeout = '3s';
ALTER ROLE tutor_base_app SET idle_in_transaction_session_timeout = '15s';

GRANT tutor_base_runtime TO tutor_base_app;
REVOKE CREATE ON SCHEMA public FROM tutor_base_runtime, tutor_base_app;
GRANT USAGE ON SCHEMA public TO tutor_base_runtime;

DO $schema_usage$
BEGIN
    IF NOT has_schema_privilege('tutor_base_app', 'public', 'USAGE') THEN
        RAISE EXCEPTION 'Cannot provision runtime role: USAGE on schema public is unavailable';
    END IF;
END
$schema_usage$;

GRANT SELECT, INSERT, UPDATE ON TABLE public.student TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.account TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.account_activation TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.account_session TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.daily_check_in TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.user_points TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.card_collection TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.article_answer_keys TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.article_question_submissions TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.wrong_questions TO tutor_base_runtime;
GRANT USAGE ON SEQUENCE public.student_id_seq, public.account_id_seq,
    public.account_activation_id_seq, public.account_session_id_seq,
    public.daily_check_in_id_seq, public.card_collection_id_seq,
    public.article_answer_keys_id_seq, public.article_question_submissions_id_seq,
    public.wrong_questions_id_seq
    TO tutor_base_runtime;

-- The application login is not a table owner and must pass these policies.
-- Explicitly enabling RLS makes the policies effective on plain PostgreSQL as well as Supabase.
ALTER TABLE public.student ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_activation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_check_in ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_points ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.card_collection ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.article_answer_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.article_question_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wrong_questions ENABLE ROW LEVEL SECURITY;

DO $policies$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.student'::regclass
                   AND polname = 'tutor_base_runtime_student_select') THEN
        CREATE POLICY tutor_base_runtime_student_select ON public.student
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.student'::regclass
                   AND polname = 'tutor_base_runtime_student_insert') THEN
        CREATE POLICY tutor_base_runtime_student_insert ON public.student
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.student'::regclass
                   AND polname = 'tutor_base_runtime_student_update') THEN
        CREATE POLICY tutor_base_runtime_student_update ON public.student
            FOR UPDATE TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account'::regclass
                   AND polname = 'tutor_base_runtime_account_select') THEN
        CREATE POLICY tutor_base_runtime_account_select ON public.account
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account'::regclass
                   AND polname = 'tutor_base_runtime_account_insert') THEN
        CREATE POLICY tutor_base_runtime_account_insert ON public.account
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account'::regclass
                   AND polname = 'tutor_base_runtime_account_update') THEN
        CREATE POLICY tutor_base_runtime_account_update ON public.account
            FOR UPDATE TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_activation'::regclass
                   AND polname = 'tutor_base_runtime_activation_select') THEN
        CREATE POLICY tutor_base_runtime_activation_select ON public.account_activation
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_activation'::regclass
                   AND polname = 'tutor_base_runtime_activation_insert') THEN
        CREATE POLICY tutor_base_runtime_activation_insert ON public.account_activation
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_activation'::regclass
                   AND polname = 'tutor_base_runtime_activation_update') THEN
        CREATE POLICY tutor_base_runtime_activation_update ON public.account_activation
            FOR UPDATE TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_session'::regclass
                   AND polname = 'tutor_base_runtime_session_select') THEN
        CREATE POLICY tutor_base_runtime_session_select ON public.account_session
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_session'::regclass
                   AND polname = 'tutor_base_runtime_session_insert') THEN
        CREATE POLICY tutor_base_runtime_session_insert ON public.account_session
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_session'::regclass
                   AND polname = 'tutor_base_runtime_session_update') THEN
        CREATE POLICY tutor_base_runtime_session_update ON public.account_session
            FOR UPDATE TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account_session'::regclass
                   AND polname = 'tutor_base_runtime_session_delete') THEN
        CREATE POLICY tutor_base_runtime_session_delete ON public.account_session
            FOR DELETE TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.daily_check_in'::regclass
                   AND polname = 'tutor_base_runtime_check_in_select') THEN
        CREATE POLICY tutor_base_runtime_check_in_select ON public.daily_check_in
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.daily_check_in'::regclass
                   AND polname = 'tutor_base_runtime_check_in_insert') THEN
        CREATE POLICY tutor_base_runtime_check_in_insert ON public.daily_check_in
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.user_points'::regclass
                   AND polname = 'tutor_base_runtime_points_select') THEN
        CREATE POLICY tutor_base_runtime_points_select ON public.user_points
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.user_points'::regclass
                   AND polname = 'tutor_base_runtime_points_insert') THEN
        CREATE POLICY tutor_base_runtime_points_insert ON public.user_points
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.user_points'::regclass
                   AND polname = 'tutor_base_runtime_points_update') THEN
        CREATE POLICY tutor_base_runtime_points_update ON public.user_points
            FOR UPDATE TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.card_collection'::regclass
                   AND polname = 'tutor_base_runtime_card_select') THEN
        CREATE POLICY tutor_base_runtime_card_select ON public.card_collection
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.card_collection'::regclass
                   AND polname = 'tutor_base_runtime_card_insert') THEN
        CREATE POLICY tutor_base_runtime_card_insert ON public.card_collection
            FOR INSERT TO tutor_base_runtime WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.article_answer_keys'::regclass
                   AND polname = 'tutor_base_runtime_answer_keys_all') THEN
        CREATE POLICY tutor_base_runtime_answer_keys_all ON public.article_answer_keys
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.article_question_submissions'::regclass
                   AND polname = 'tutor_base_runtime_submissions_all') THEN
        CREATE POLICY tutor_base_runtime_submissions_all ON public.article_question_submissions
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.wrong_questions'::regclass
                   AND polname = 'tutor_base_runtime_wrong_questions_all') THEN
        CREATE POLICY tutor_base_runtime_wrong_questions_all ON public.wrong_questions
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
END
$policies$;

COMMIT;
