-- 多管理员下的学员认领：记录认领该学员的管理员账户与认领时间。
-- 新建学员由创建它的管理员自动认领；认领、释放与删除都通过 FOR UPDATE 串行化。
-- claimed_by 为空表示未认领；claimed_by 与 claimed_at 必须同时存在或同时缺失。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

ALTER TABLE public.student
    ADD COLUMN claimed_by bigint,
    ADD COLUMN claimed_at timestamp with time zone;

ALTER TABLE public.student
    ADD CONSTRAINT student_claimed_by_fkey
        FOREIGN KEY (claimed_by) REFERENCES public.account (id) ON DELETE SET NULL,
    ADD CONSTRAINT student_claim_state_check
        CHECK ((claimed_by IS NULL) = (claimed_at IS NULL));

-- claimed_by 的 ON DELETE SET NULL 只会把 claimed_by 置空；触发器同步清空 claimed_at，
-- 避免账户删除时破坏 paired-state 约束。
CREATE FUNCTION public.clear_student_claim_timestamp()
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

CREATE TRIGGER student_clear_claim_timestamp
    BEFORE INSERT OR UPDATE ON public.student
    FOR EACH ROW
    EXECUTE FUNCTION public.clear_student_claim_timestamp();

CREATE INDEX student_claimed_by_idx
    ON public.student (claimed_by)
    WHERE claimed_by IS NOT NULL;
