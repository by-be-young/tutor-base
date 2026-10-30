-- 学科改名：化学 → 高中化学，英语 → 高中英语（初中英语保持不变）。
--
-- 文章 ID（articles.json 的 id、courseware_catalog.article_id）与目录结构中的索引一律不变，
-- 这里只迁移三类「以文本保存学科名」的存量数据：
--   1. courseware_catalog.subject（课件目录的学科归属）
--   2. admin_subject_claim.subject（学科认领）
--   3. student.subjects（学员已开通科目）
-- 否则改名后旧数据会与新学科名失配，导致已认领课件、已开通科目在页面上消失。
--
-- 本脚本必须是幂等的：database/reconciliation/2026-10-30_subject_rename.sql 可手工重复执行。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

-- ----------------------------------------------------------------------------
-- V20：课件目录与学科认领
-- ----------------------------------------------------------------------------
UPDATE public.courseware_catalog
SET subject = '高中化学'
WHERE subject = '化学';

UPDATE public.courseware_catalog
SET subject = '高中英语'
WHERE subject = '英语';

UPDATE public.admin_subject_claim
SET subject = '高中化学'
WHERE subject = '化学';

UPDATE public.admin_subject_claim
SET subject = '高中英语'
WHERE subject = '英语';

-- ----------------------------------------------------------------------------
-- V20：学员已开通科目（text[]，逐元素改名，去重并按字典序稳定排列）
-- ----------------------------------------------------------------------------
UPDATE public.student AS student
SET subjects = mapped.subjects
FROM (
    SELECT source.id,
           COALESCE(
               array_agg(DISTINCT renamed.item ORDER BY renamed.item),
               '{}'::text[]
           ) AS subjects
    FROM public.student AS source
    CROSS JOIN LATERAL unnest(source.subjects) AS raw(item)
    CROSS JOIN LATERAL (
        SELECT CASE
                   WHEN raw.item = '化学' THEN '高中化学'
                   WHEN raw.item = '英语' THEN '高中英语'
                   ELSE raw.item
               END AS item
    ) AS renamed
    WHERE source.subjects @> ARRAY['化学']::text[]
       OR source.subjects @> ARRAY['英语']::text[]
    GROUP BY source.id
) AS mapped
WHERE student.id = mapped.id;
