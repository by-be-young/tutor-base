-- ============================================================================
-- 学员认领（learner claiming）对齐现有 Supabase 数据库：V11（2026-10-27）
-- ----------------------------------------------------------------------------
-- 本地 Flyway 被禁用时（DATABASE_MIGRATIONS_ENABLED=false），在 Supabase SQL
-- Editor 中整体执行本文件即可对齐现有数据库。全部语句可重复执行。
--
-- 覆盖内容：
--   V11 public.student.claimed_by / claimed_at：记录认领学员的管理员账户与时间
--       student_claimed_by_fkey      claimed_by → account，账户删除时置空
--       student_claim_state_check    claimed_by 与 claimed_at 同时为空或同时非空
--       student_claimed_by_idx       仅索引已认领行（WHERE claimed_by IS NOT NULL）
--       student_clear_claim_timestamp 账户删除置空 claimed_by 时同步清空 claimed_at
-- ============================================================================

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

-- ----------------------------------------------------------------------------
-- V11：认领列（新列默认 NULL，现有学习者保持未认领状态）
-- ----------------------------------------------------------------------------
ALTER TABLE public.student
    ADD COLUMN IF NOT EXISTS claimed_by bigint,
    ADD COLUMN IF NOT EXISTS claimed_at timestamp with time zone;

DO $student_claim_fkey$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_claimed_by_fkey'
    ) THEN
        EXECUTE 'ALTER TABLE public.student '
             || 'ADD CONSTRAINT student_claimed_by_fkey '
             || 'FOREIGN KEY (claimed_by) REFERENCES public.account (id) '
             || 'ON DELETE SET NULL NOT VALID';
    END IF;
    EXECUTE 'ALTER TABLE public.student VALIDATE CONSTRAINT student_claimed_by_fkey';
END
$student_claim_fkey$;

DO $student_claim_state$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_claim_state_check'
    ) THEN
        EXECUTE 'ALTER TABLE public.student '
             || 'ADD CONSTRAINT student_claim_state_check '
             || 'CHECK ((claimed_by IS NULL) = (claimed_at IS NULL)) NOT VALID';
    END IF;
    EXECUTE 'ALTER TABLE public.student VALIDATE CONSTRAINT student_claim_state_check';
END
$student_claim_state$;

-- claimed_by 的 ON DELETE SET NULL 只会把 claimed_by 置空；触发器同步清空 claimed_at。
CREATE OR REPLACE FUNCTION public.clear_student_claim_timestamp()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.claimed_by IS NULL THEN
        NEW.claimed_at = NULL;
    END IF;
    RETURN NEW;
END;
$function$;

DO $student_clear_claim_timestamp_trigger$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = 'public.student'::regclass
          AND tgname = 'student_clear_claim_timestamp'
          AND NOT tgisinternal
    ) THEN
        EXECUTE 'CREATE TRIGGER student_clear_claim_timestamp '
             || 'BEFORE INSERT OR UPDATE ON public.student '
             || 'FOR EACH ROW '
             || 'EXECUTE FUNCTION public.clear_student_claim_timestamp()';
    END IF;
END
$student_clear_claim_timestamp_trigger$;

CREATE INDEX IF NOT EXISTS student_claimed_by_idx
    ON public.student (claimed_by)
    WHERE claimed_by IS NOT NULL;

COMMIT;
