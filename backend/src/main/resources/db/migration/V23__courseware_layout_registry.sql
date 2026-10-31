-- 课件托管 P2：布局注册表（在线编辑器与前端渲染共用）
--
-- 布局指令写在 markdown 的 HTML 注释里（`<!-- @layout:A -->`），对普通 Markdown 渲染
-- 与既有 `【题干N】` / `【@N】` 解析都无影响；缺省时用文档 front-matter 的 defaultLayout。

CREATE TABLE IF NOT EXISTS public.course_layout (
    layout_id text PRIMARY KEY,
    name text NOT NULL,
    component text NOT NULL,
    allowed_blocks text[] NOT NULL DEFAULT '{}',
    version integer NOT NULL DEFAULT 1,
    active boolean NOT NULL DEFAULT true,
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT course_layout_id_check CHECK (layout_id ~ '^[A-Z][A-Z0-9_-]{0,15}$')
);

INSERT INTO public.course_layout (layout_id, name, component, allowed_blocks, version, active)
VALUES
    ('A', '讲义式（知识点 + 例题双栏）', 'LayoutLecture',
     ARRAY['paragraph', 'list', 'formula', 'image', 'table', 'question'], 1, true),
    ('B', '练习式（题干与作答区分离）', 'LayoutPractice',
     ARRAY['paragraph', 'list', 'formula', 'image', 'table', 'question'], 1, true),
    ('C', '对比表', 'LayoutComparison',
     ARRAY['paragraph', 'table', 'formula', 'callout'], 1, true),
    ('D', '图文卡片', 'LayoutMediaCard',
     ARRAY['paragraph', 'image', 'formula', 'callout'], 1, true)
ON CONFLICT (layout_id) DO NOTHING;

ALTER TABLE public.course_layout ENABLE ROW LEVEL SECURITY;

DO $v23_runtime$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime') THEN
        RETURN;
    END IF;

    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.course_layout TO tutor_base_runtime;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.course_layout'::regclass
          AND polname = 'tutor_base_runtime_course_layout_all'
    ) THEN
        CREATE POLICY tutor_base_runtime_course_layout_all
            ON public.course_layout FOR ALL TO tutor_base_runtime
            USING (true) WITH CHECK (true);
    END IF;
END
$v23_runtime$;
