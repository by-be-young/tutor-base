-- 测试学员标记：is_test 表示该学员是测试学员；is_special_test 表示特殊测试学员（全局最多一个）。
-- 特殊测试学员拥有全部课件权限（permissions 由 courseware_catalog 补齐），并由应用返回固定高积分。
-- 测试学员不参与管理员侧的活跃度与动态统计。
--
-- 本脚本必须是幂等的：数据库可能已经通过 database/reconciliation/2026-10-30_test_learners.sql
-- 手工对齐过这些对象，但 Flyway 历史表尚未记账。全新数据库执行结果与 reconciliation 完全一致。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

-- ----------------------------------------------------------------------------
-- V13：测试学员标记列
-- ----------------------------------------------------------------------------
ALTER TABLE public.student
    ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_special_test boolean NOT NULL DEFAULT false;

-- 特殊测试学员必须同时是测试学员。
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

-- 全局最多一个特殊测试学员：部分唯一索引只约束 is_special_test = true 的行。
CREATE UNIQUE INDEX IF NOT EXISTS student_single_special_test_idx
    ON public.student (is_special_test)
    WHERE is_special_test;

-- ----------------------------------------------------------------------------
-- V13：backfill 既有学员标记（可重复执行）
-- 除 pyt、Mel.（用户名比较大小写不敏感）外的既有学员全部标记为测试学员；
-- test 同时标记为特殊测试学员。
-- ----------------------------------------------------------------------------
UPDATE public.student
SET is_test = true
WHERE lower(username) NOT IN ('pyt', 'mel.');

UPDATE public.student
SET is_test = true,
    is_special_test = true
WHERE lower(username) = 'test';

-- 特殊测试学员拥有全部课件：把 courseware_catalog 全部 article_id 写入其 permissions。
-- article_id 是 bigint，permissions 是 integer[]，需要显式转换为 int4；catalog 为空时保持原值。
UPDATE public.student AS special
SET permissions = catalog_articles.article_ids
FROM (
    SELECT array_agg(catalog.article_id::integer ORDER BY catalog.article_id) AS article_ids
    FROM public.courseware_catalog AS catalog
) AS catalog_articles
WHERE special.is_special_test
  AND catalog_articles.article_ids IS NOT NULL;
