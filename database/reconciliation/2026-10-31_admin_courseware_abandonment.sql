-- ============================================================================
-- 已放弃课件（courseware abandonment）对齐现有数据库：V19（2026-10-31）
-- ----------------------------------------------------------------------------
-- 本地 Flyway 被禁用时（DATABASE_MIGRATIONS_ENABLED=false），在 Supabase SQL
-- Editor 中整体执行本文件即可对齐现有数据库。全部语句可重复执行。
--
-- 重要：本文件不改写 public.flyway_schema_history。执行后实际 schema 已含 V19，
-- 而历史表可能仍是 V18。V19__admin_courseware_abandonment.sql 已幂等，因此下一次
-- Flyway 启动会成功执行并记账，不会因对象已存在而失败；本文件的语句应保持与
-- V19 migration 的对象定义完全一致，避免两者产生结构漂移。
--
-- 覆盖内容：
--   V19 public.admin_courseware_abandonment  管理员主动放弃的课件
--       admin_courseware_abandonment_pkey 同一管理员同一课件只保留一条
--       admin_courseware_abandonment_article_idx 按课件检索放弃者
--   运行时角色存在时补发 SELECT/INSERT/UPDATE/DELETE 与 RLS policy。
-- ============================================================================

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = public;

CREATE TABLE IF NOT EXISTS public.admin_courseware_abandonment (
    admin_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    article_id bigint NOT NULL REFERENCES public.courseware_catalog (article_id) ON DELETE CASCADE,
    abandoned_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT admin_courseware_abandonment_pkey
        PRIMARY KEY (admin_account_id, article_id)
);

CREATE INDEX IF NOT EXISTS admin_courseware_abandonment_article_idx
    ON public.admin_courseware_abandonment (article_id);

ALTER TABLE public.admin_courseware_abandonment ENABLE ROW LEVEL SECURITY;

DO $v19_runtime$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        RETURN;
    END IF;

    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_courseware_abandonment TO tutor_base_runtime';

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.admin_courseware_abandonment'::regclass
          AND polname = 'tutor_base_runtime_admin_courseware_abandonment_all'
    ) THEN
        EXECUTE 'CREATE POLICY tutor_base_runtime_admin_courseware_abandonment_all ON public.admin_courseware_abandonment FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true)';
    END IF;
END
$v19_runtime$;

COMMIT;
