-- 已放弃课件：管理员主动放弃某篇课件后，该课件不再出现在该管理员的“待认领”中，
-- 而是进入“已放弃的课件”。放弃是管理员个人视图，其他管理员仍可按认领规则看到并认领。
--
-- 本脚本必须幂等：对象可能已通过 database/reconciliation/2026-10-31_admin_courseware_abandonment.sql
-- 手工建好但 Flyway 尚未记账。运行时角色在空数据库上可能尚不存在（provision 脚本后执行），
-- 此时跳过授权与 policy，由 provision 脚本补建；已 provision 的库在迁移时直接补齐。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

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
