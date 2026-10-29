-- 可重复执行：为已 provision 的运行时角色补齐 V12 课件表/序列权限与 RLS policy。
-- 与 backend/src/main/resources/db/migration/V14__grant_courseware_runtime_privileges.sql 对象一致。
-- 注意：手工执行不会写入 flyway_schema_history；Flyway 仍会按 V14 正常记账（脚本幂等）。

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = public;

DO $v14_runtime_grants$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        RETURN;
    END IF;

    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.courseware_catalog TO tutor_base_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_subject_claim TO tutor_base_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_courseware_claim TO tutor_base_runtime';

    IF to_regclass('public.admin_subject_claim_id_seq') IS NOT NULL THEN
        EXECUTE 'GRANT USAGE ON SEQUENCE public.admin_subject_claim_id_seq TO tutor_base_runtime';
    END IF;
    IF to_regclass('public.admin_courseware_claim_id_seq') IS NOT NULL THEN
        EXECUTE 'GRANT USAGE ON SEQUENCE public.admin_courseware_claim_id_seq TO tutor_base_runtime';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.courseware_catalog'::regclass
          AND polname = 'tutor_base_runtime_courseware_catalog_all'
    ) THEN
        EXECUTE 'CREATE POLICY tutor_base_runtime_courseware_catalog_all ON public.courseware_catalog FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true)';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.admin_subject_claim'::regclass
          AND polname = 'tutor_base_runtime_admin_subject_claim_all'
    ) THEN
        EXECUTE 'CREATE POLICY tutor_base_runtime_admin_subject_claim_all ON public.admin_subject_claim FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true)';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.admin_courseware_claim'::regclass
          AND polname = 'tutor_base_runtime_admin_courseware_claim_all'
    ) THEN
        EXECUTE 'CREATE POLICY tutor_base_runtime_admin_courseware_claim_all ON public.admin_courseware_claim FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true)';
    END IF;
END
$v14_runtime_grants$;

COMMIT;
