-- Provision the least-privilege login used by the Java backend.
-- Run as the Supabase postgres role. This script intentionally does not set a password.
--
-- 本脚本必须在 public schema 可见的会话中执行。Supabase 某些入口（例如 Database → Roles 的
-- SQL Editor 上下文）只把 `search_path` 解析到 pg_catalog，此时 `relation "public.student"
-- does not exist` 说明脚本找不到 public 表，而不是表真的不存在。脚本自身先把 search_path
-- 指向 public，避免依赖执行上下文。

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = public;

DO $roles$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        CREATE ROLE tutor_base_runtime
            NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_app') THEN
        CREATE ROLE tutor_base_app
            LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS
            CONNECTION LIMIT 20;
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
    CONNECTION LIMIT 20;
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

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.student TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.account TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.account_activation TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.account_session TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.daily_check_in TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.user_points TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.task_claims TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.card_collection TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.article_answer_keys TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.article_question_submissions TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.wrong_questions TO tutor_base_runtime;
GRANT SELECT, INSERT ON TABLE public.achievement_claims TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.task_assignments TO tutor_base_runtime;
-- 卡池目录由运营通过 migration/reconciliation 维护，运行角色只读。
GRANT SELECT ON TABLE public.card_sets TO tutor_base_runtime;
GRANT SELECT ON TABLE public.card_definitions TO tutor_base_runtime;
-- 课件目录由管理端同步接口写入；认领关系由管理员增删。
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.courseware_catalog TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_subject_claim TO tutor_base_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_courseware_claim TO tutor_base_runtime;
GRANT USAGE ON SEQUENCE public.student_id_seq, public.account_id_seq,
    public.account_activation_id_seq, public.account_session_id_seq,
    public.daily_check_in_id_seq, public.task_claims_id_seq, public.card_collection_id_seq,
    public.article_answer_keys_id_seq, public.article_question_submissions_id_seq,
    public.wrong_questions_id_seq, public.achievement_claims_id_seq, public.task_assignments_id_seq,
    public.admin_subject_claim_id_seq, public.admin_courseware_claim_id_seq
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
ALTER TABLE public.achievement_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.card_sets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.card_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courseware_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_subject_claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_courseware_claim ENABLE ROW LEVEL SECURITY;

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
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.student'::regclass
                   AND polname = 'tutor_base_runtime_student_delete') THEN
        CREATE POLICY tutor_base_runtime_student_delete ON public.student
            FOR DELETE TO tutor_base_runtime USING (true);
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
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.account'::regclass
                   AND polname = 'tutor_base_runtime_account_delete') THEN
        CREATE POLICY tutor_base_runtime_account_delete ON public.account
            FOR DELETE TO tutor_base_runtime USING (true);
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
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.task_claims'::regclass
                   AND polname = 'tutor_base_runtime_task_claims_all') THEN
        CREATE POLICY tutor_base_runtime_task_claims_all ON public.task_claims
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.achievement_claims'::regclass
                   AND polname = 'tutor_base_runtime_achievement_claims_all') THEN
        CREATE POLICY tutor_base_runtime_achievement_claims_all ON public.achievement_claims
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.task_assignments'::regclass
                   AND polname = 'tutor_base_runtime_task_assignments_all') THEN
        CREATE POLICY tutor_base_runtime_task_assignments_all ON public.task_assignments
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.card_sets'::regclass
                   AND polname = 'tutor_base_runtime_card_sets_select') THEN
        CREATE POLICY tutor_base_runtime_card_sets_select ON public.card_sets
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.card_definitions'::regclass
                   AND polname = 'tutor_base_runtime_card_definitions_select') THEN
        CREATE POLICY tutor_base_runtime_card_definitions_select ON public.card_definitions
            FOR SELECT TO tutor_base_runtime USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.courseware_catalog'::regclass
                   AND polname = 'tutor_base_runtime_courseware_catalog_all') THEN
        CREATE POLICY tutor_base_runtime_courseware_catalog_all ON public.courseware_catalog
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.admin_subject_claim'::regclass
                   AND polname = 'tutor_base_runtime_admin_subject_claim_all') THEN
        CREATE POLICY tutor_base_runtime_admin_subject_claim_all ON public.admin_subject_claim
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = 'public.admin_courseware_claim'::regclass
                   AND polname = 'tutor_base_runtime_admin_courseware_claim_all') THEN
        CREATE POLICY tutor_base_runtime_admin_courseware_claim_all ON public.admin_courseware_claim
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
END
$policies$;

COMMIT;
