-- 目录登记：目录树由已发布篇目与草稿的路径推导，空目录没有文件就无法存在。
-- 允许「新建子目录」后先建目录、稍后再放课件，因此单独登记老师命名空间下的目录路径。
--
-- 幂等：database/reconciliation/2026-11-13_course_folder.sql 可手工重复执行。

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

DO $v25_2_runtime$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        RETURN;
    END IF;

    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.course_folder TO tutor_base_runtime;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.course_folder'::regclass
          AND polname = 'tutor_base_runtime_course_folder_all'
    ) THEN
        CREATE POLICY tutor_base_runtime_course_folder_all ON public.course_folder
            FOR ALL TO tutor_base_runtime USING (true) WITH CHECK (true);
    END IF;
END
$v25_2_runtime$;
