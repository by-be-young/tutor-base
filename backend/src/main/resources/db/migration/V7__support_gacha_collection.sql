-- 收藏卡不再绑定里程碑积分：milestone_points 允许为空，移除旧唯一键。
-- 生产库已由 database/reconciliation/2026-10-20_gacha_card_pool.sql 手工对齐，
-- 其中 milestone_points 列已被移除；因此这里仅在该列存在时执行（幂等）。
DO $v7_support_gacha_collection$
BEGIN
    IF to_regclass('public.card_collection') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'card_collection'
             AND column_name = 'milestone_points'
       )
    THEN
        ALTER TABLE public.card_collection
            ALTER COLUMN milestone_points DROP NOT NULL;

        ALTER TABLE public.card_collection
            DROP CONSTRAINT IF EXISTS card_collection_student_milestone_unique;

        CREATE UNIQUE INDEX IF NOT EXISTS card_collection_student_milestone_unique
            ON public.card_collection (student_id, milestone_points)
            WHERE milestone_points IS NOT NULL;
    END IF;
END
$v7_support_gacha_collection$;
