-- 多管理员下的学员认领：记录认领该学员的管理员账户与认领时间。
-- 新建学员由创建它的管理员自动认领；认领、释放与删除都通过 FOR UPDATE 串行化。
-- claimed_by 为空表示未认领；claimed_by 与 claimed_at 必须同时存在或同时缺失。
--
-- 本脚本必须是幂等的（与 V7 同理）：数据库可能已经包含这些对象但 Flyway 尚未记账。
-- 1）历史上曾有一次部署对「已手工执行过 reconciliation、历史表仍停在 V10」的库直接执行本
--    migration，因列已存在而失败并触发自动回滚，已修正为幂等写法；
-- 2）全新数据库执行时结果与原始 DDL 完全一致，FlywayMigrationTest 覆盖两种路径。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

ALTER TABLE public.student
    ADD COLUMN IF NOT EXISTS claimed_by bigint,
    ADD COLUMN IF NOT EXISTS claimed_at timestamp with time zone;

DO $v11_constraints$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_claimed_by_fkey'
    ) THEN
        ALTER TABLE public.student
            ADD CONSTRAINT student_claimed_by_fkey
                FOREIGN KEY (claimed_by) REFERENCES public.account (id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.student'::regclass
          AND conname = 'student_claim_state_check'
    ) THEN
        ALTER TABLE public.student
            ADD CONSTRAINT student_claim_state_check
                CHECK ((claimed_by IS NULL) = (claimed_at IS NULL));
    END IF;
END
$v11_constraints$;

-- claimed_by 的 ON DELETE SET NULL 只会把 claimed_by 置空；触发器同步清空 claimed_at，
-- 避免账户删除时破坏 paired-state 约束。
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

DO $v11_trigger$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgrelid = 'public.student'::regclass
          AND tgname = 'student_clear_claim_timestamp'
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER student_clear_claim_timestamp
            BEFORE INSERT OR UPDATE ON public.student
            FOR EACH ROW
            EXECUTE FUNCTION public.clear_student_claim_timestamp();
    END IF;
END
$v11_trigger$;

CREATE INDEX IF NOT EXISTS student_claimed_by_idx
    ON public.student (claimed_by)
    WHERE claimed_by IS NOT NULL;
