-- 对齐已在共享环境执行过的 V25.1（草稿目标路径与标题）。可重复执行。
--
-- 用法：psql "$DATABASE_URL" -f database/reconciliation/2026-11-13_course_draft_path.sql

ALTER TABLE public.course_draft
    ADD COLUMN IF NOT EXISTS logical_path text,
    ADD COLUMN IF NOT EXISTS title text;
