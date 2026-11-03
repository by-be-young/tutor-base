-- ============================================================================
-- V26（计划中，尚未纳入 Flyway）：把 courseware_catalog 的唯一性收窄到老师维度
-- ============================================================================
-- 为什么需要它：V21 给 courseware_catalog 加了 teacher_account_id，但主键仍是全局
-- article_id，因此"两位老师各自拥有 article_id = 3" 无法表达。V26 把主键换成
-- (teacher_account_id, article_id)，并解除三张旧认领表对 article_id 单列的外键。
--
-- 风险与前提（见 docs/courseware-v26-migration-runbook.md）：
--   1. 需要停机窗口：DDL 会锁 courseware_catalog 与三张认领表；
--   2. 三张旧认领表（claim / exclusion / abandonment）失去外键，之后只作只读历史；
--      写入侧早已可由 tutor.courseware.legacy-claims-enabled=false 收紧到 410；
--   3. 回滚脚本只能在"回滚前不存在跨老师重复 article_id"时成功，脚本里会先断言。
--
-- 执行方式：审核通过后移动到 backend/src/main/resources/db/migration/ 并随下一个
-- 版本发布；现在放在 database/planned/ 下，Flyway 不会扫描到。
-- ============================================================================

BEGIN;

-- 0) 前置断言：teacher_account_id 已回填（V21 已做），legacy_article_id 已回填
DO $v26_precheck$
DECLARE
    missing_teacher bigint;
BEGIN
    SELECT count(*) INTO missing_teacher
    FROM public.courseware_catalog
    WHERE teacher_account_id IS NULL;
    IF missing_teacher > 0 THEN
        RAISE EXCEPTION '课程目录有 % 行缺少 teacher_account_id，先回填再执行 V26', missing_teacher;
    END IF;
END
$v26_precheck$;

-- 1) teacher_account_id 收紧为 NOT NULL（主键需要）
ALTER TABLE public.courseware_catalog
    ALTER COLUMN teacher_account_id SET NOT NULL;

-- 2) 解除三张旧认领表对 article_id 单列的外键
ALTER TABLE public.admin_courseware_claim
    DROP CONSTRAINT IF EXISTS admin_courseware_claim_article_id_fkey;
ALTER TABLE public.admin_courseware_exclusion
    DROP CONSTRAINT IF EXISTS admin_courseware_exclusion_article_id_fkey;
ALTER TABLE public.admin_courseware_abandonment
    DROP CONSTRAINT IF EXISTS admin_courseware_abandonment_article_id_fkey;

-- 3) 换主键：article_id 单列 → (teacher_account_id, article_id)
ALTER TABLE public.courseware_catalog
    DROP CONSTRAINT IF EXISTS courseware_catalog_pkey;
ALTER TABLE public.courseware_catalog
    ADD CONSTRAINT courseware_catalog_pkey PRIMARY KEY (teacher_account_id, article_id);

-- 4) 迁移前全局 article_id 是唯一的，这里保留"同一老师内 legacy id 唯一"的约束，
--    供 student.permissions / article_answer_keys 按 legacy id 反查时保持一对一。
CREATE UNIQUE INDEX IF NOT EXISTS courseware_catalog_teacher_legacy_unique
    ON public.courseware_catalog (teacher_account_id, legacy_article_id)
    WHERE legacy_article_id IS NOT NULL;

-- 5) 按老师 + legacy id 反查的支撑索引（权限与答案键回填都用它）
CREATE INDEX IF NOT EXISTS courseware_catalog_legacy_idx
    ON public.courseware_catalog (legacy_article_id)
    WHERE legacy_article_id IS NOT NULL;

COMMIT;
