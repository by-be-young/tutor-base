-- 草稿记录目标位置：从目录树任意一级新建课件时，草稿必须记住它该发布到哪个路径、叫什么名字。
--
-- 之前草稿只有正文，发布时路径靠 catalog 标题或正文首标题推导，无法在「某一级目录下新建」。
-- 两列都可空：老草稿保持原推导逻辑；新草稿带上用户选定的路径与标题。
--
-- 幂等：database/reconciliation/2026-11-13_course_draft_path.sql 可手工重复执行。

ALTER TABLE public.course_draft
    ADD COLUMN IF NOT EXISTS logical_path text,
    ADD COLUMN IF NOT EXISTS title text;
