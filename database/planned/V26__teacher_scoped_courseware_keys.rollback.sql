-- ============================================================================
-- V26 回滚脚本（计划中）：把 courseware_catalog 主键恢复为全局 article_id
-- ============================================================================
-- 只有在"当前不存在跨老师重复 article_id"时才能回滚；脚本先断言再动手。
-- 回滚会丢失"同一 article_id 属于多位老师"的能力，因此回滚后必须同时把
-- tutor.courseware.legacy-claims-enabled 恢复为 true，避免旧认领页写入 410。
-- ============================================================================

BEGIN;

-- 0) 断言：article_id 仍全局唯一
DO $v26_rollback_precheck$
DECLARE
    duplicated bigint;
BEGIN
    SELECT count(*) INTO duplicated
    FROM (
        SELECT article_id FROM public.courseware_catalog GROUP BY article_id HAVING count(*) > 1
    ) AS dup;
    IF duplicated > 0 THEN
        RAISE EXCEPTION '存在 % 个跨老师重复的 article_id，无法回滚到全局主键', duplicated;
    END IF;
END
$v26_rollback_precheck$;

-- 1) 去掉老师维度的索引与主键
DROP INDEX IF EXISTS public.courseware_catalog_teacher_legacy_unique;
ALTER TABLE public.courseware_catalog
    DROP CONSTRAINT IF EXISTS courseware_catalog_pkey;

-- 2) 恢复全局主键
ALTER TABLE public.courseware_catalog
    ADD CONSTRAINT courseware_catalog_pkey PRIMARY KEY (article_id);

-- 3) 恢复三张旧认领表的外键（数据未改动，理应全部满足）
ALTER TABLE public.admin_courseware_claim
    ADD CONSTRAINT admin_courseware_claim_article_id_fkey
        FOREIGN KEY (article_id) REFERENCES public.courseware_catalog (article_id) ON DELETE CASCADE;
ALTER TABLE public.admin_courseware_exclusion
    ADD CONSTRAINT admin_courseware_exclusion_article_id_fkey
        FOREIGN KEY (article_id) REFERENCES public.courseware_catalog (article_id) ON DELETE CASCADE;
ALTER TABLE public.admin_courseware_abandonment
    ADD CONSTRAINT admin_courseware_abandonment_article_id_fkey
        FOREIGN KEY (article_id) REFERENCES public.courseware_catalog (article_id) ON DELETE CASCADE;

-- 4) 恢复可空性（与 V21 后的状态一致）
ALTER TABLE public.courseware_catalog
    ALTER COLUMN teacher_account_id DROP NOT NULL;

COMMIT;
