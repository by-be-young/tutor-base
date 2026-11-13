-- 对齐已在共享环境执行过的 V25.2（课件目录登记表）。可重复执行。
--
-- 用法：psql "$DATABASE_URL" -f database/reconciliation/2026-11-13_course_folder.sql

CREATE TABLE IF NOT EXISTS public.course_folder (
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    path text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (teacher_account_id, path),
    CONSTRAINT course_folder_path_check CHECK (path <> '' AND path NOT LIKE '%..%')
);

CREATE INDEX IF NOT EXISTS course_folder_teacher_idx
    ON public.course_folder (teacher_account_id, path);

ALTER TABLE public.course_folder ENABLE ROW LEVEL SECURITY;
