-- ============================================================================
-- 测试学员（test learners）对齐现有 Supabase 数据库：V13（2026-10-30）
-- ----------------------------------------------------------------------------
-- 本地 Flyway 被禁用时（DATABASE_MIGRATIONS_ENABLED=false），在 Supabase SQL
-- Editor 中整体执行本文件即可对齐现有数据库。全部语句可重复执行。
--
-- 重要：本文件不改写 public.flyway_schema_history。执行后实际 schema 已含 V13，
-- 而历史表可能仍是 V12；V13__add_test_learners.sql 已写成幂等，因此下一次
-- Flyway 启动会成功执行并记账，不会因对象已存在而失败；本文件的语句必须与
-- V13 migration 的对象定义完全一致，避免两者产生结构漂移。
--
-- 覆盖内容：
--   V13 public.student.is_test / is_special_test   测试学员与特殊测试学员标记
--       student_special_test_state_check          特殊测试必须同时是测试学员
--       student_single_special_test_idx           全局最多一个特殊测试学员
--   V13 backfill：除 pyt、Mel. 外的既有学员标记为测试学员；test 标记为特殊测试学员；
--       特殊测试学员的 permissions 补齐为 courseware_catalog 全部 article_id。
-- ============================================================================

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

-- ----------------------------------------------------------------------------
-- V13：测试学员标记列
-- ----------------------------------------------------------------------------
ALTER TABLE public.student
    ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_special_test boolean NOT NULL DEFAULT false;

DO $v13_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_special_test_state_check'
    ) THEN
        ALTER TABLE public.student
            ADD CONSTRAINT student_special_test_state_check
                CHECK (NOT is_special_test OR is_test);
    END IF;
END
$v13_constraints$;

CREATE UNIQUE INDEX IF NOT EXISTS student_single_special_test_idx
    ON public.student (is_special_test)
    WHERE is_special_test;

-- ----------------------------------------------------------------------------
-- V13：backfill 既有学员标记
-- ----------------------------------------------------------------------------
UPDATE public.student
SET is_test = true
WHERE lower(username) NOT IN ('pyt', 'mel.');

UPDATE public.student
SET is_test = true,
    is_special_test = true
WHERE lower(username) = 'test';

UPDATE public.student AS special
SET permissions = catalog_articles.article_ids
FROM (
    SELECT array_agg(catalog.article_id::integer ORDER BY catalog.article_id) AS article_ids
    FROM public.courseware_catalog AS catalog
) AS catalog_articles
WHERE special.is_special_test
  AND catalog_articles.article_ids IS NOT NULL;

COMMIT;
