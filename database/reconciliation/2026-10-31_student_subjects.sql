-- 存量库手工对齐：学员科目与文章授权分离（对应 Flyway V18）。
-- 可重复执行；已存在 subjects 列/约束时跳过。
--
-- 执行顺序：在 V17 之后、应用发布之前执行。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

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
