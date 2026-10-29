-- 运行时角色授权补齐。
--
-- V12 新增的课件目录/认领表与序列需要 SELECT/INSERT/UPDATE/DELETE/USAGE 权限；
-- 历史上这些权限由 database/operations/provision_runtime_role.sql 手工执行。
-- 本 migration 用 schema owner 身份幂等补发权限，避免“代码已发布但运行时角色未授权”
-- 导致管理端接口返回 503 dependency_unavailable。
--
-- 空数据库上运行时角色可能尚不存在（provision 脚本后执行），此时跳过；
-- 已 provision 的数据库（Supabase staging/production）会在迁移时自动补齐。
-- 本脚本可重复执行。

DO $v14_runtime_grants$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        RETURN;
    END IF;

    -- 课件目录与认领表：管理端需要读写。
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.courseware_catalog TO tutor_base_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_subject_claim TO tutor_base_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_courseware_claim TO tutor_base_runtime';

    IF to_regclass('public.admin_subject_claim_id_seq') IS NOT NULL THEN
        EXECUTE 'GRANT USAGE ON SEQUENCE public.admin_subject_claim_id_seq TO tutor_base_runtime';
    END IF;
    IF to_regclass('public.admin_courseware_claim_id_seq') IS NOT NULL THEN
        EXECUTE 'GRANT USAGE ON SEQUENCE public.admin_courseware_claim_id_seq TO tutor_base_runtime';
    END IF;

    -- V12 在角色已存在时创建 policy；这里兜底补建，保证 RLS 不会挡住运行时角色。
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
