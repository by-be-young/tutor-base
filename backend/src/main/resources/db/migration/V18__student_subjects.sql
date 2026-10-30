-- 学员科目与文章授权分离：subjects 表示管理员给学员开通的科目，permissions 表示具体文章授权。
-- 开通科目只建立科目归属，初始不授权该科目下任何文章；文章仍需在权限管理中逐篇选择。
--
-- 本脚本必须是幂等的：存量库可能已经通过
-- database/reconciliation/2026-10-31_student_subjects.sql 手工对齐过该列，
-- 但 Flyway 历史表尚未记账。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

-- ----------------------------------------------------------------------------
-- V18：学员科目列
-- ----------------------------------------------------------------------------
ALTER TABLE public.student
    ADD COLUMN IF NOT EXISTS subjects text[] NOT NULL DEFAULT '{}'::text[];

DO $v18_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_subjects_no_empty_check'
    ) THEN
        ALTER TABLE public.student
            ADD CONSTRAINT student_subjects_no_empty_check
                CHECK (array_position(subjects, '') IS NULL);
    END IF;
END
$v18_constraints$;

-- ----------------------------------------------------------------------------
-- V18：backfill 既有科目归属（可重复执行）
-- 历史上「开通科目」等于一次性授权该科全部文章，因此从现有 permissions 反推科目；
-- 已经手工对齐过的非空 subjects 保持原值。
-- ----------------------------------------------------------------------------
UPDATE public.student AS student
SET subjects = derived.subjects
FROM (
    SELECT source.id,
           COALESCE(
               array_agg(DISTINCT catalog.subject ORDER BY catalog.subject)
                   FILTER (WHERE catalog.subject IS NOT NULL),
               '{}'::text[]
           ) AS subjects
    FROM public.student AS source
    LEFT JOIN public.courseware_catalog AS catalog
      ON catalog.article_id = ANY (source.permissions)
    GROUP BY source.id
) AS derived
WHERE student.id = derived.id
  AND cardinality(student.subjects) = 0
  AND cardinality(derived.subjects) > 0;
