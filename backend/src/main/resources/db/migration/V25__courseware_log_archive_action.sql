-- 课件托管 P5：审计动作补 archive
--
-- 归档（老师停用/交接）与删除语义不同：归档保留正文与历史版本，只是不再对外供数。
-- 单独加动作值，避免把归档混进 delete 里导致审计失真。

ALTER TABLE public.course_operation_log
    DROP CONSTRAINT IF EXISTS course_operation_log_action_check;

ALTER TABLE public.course_operation_log
    ADD CONSTRAINT course_operation_log_action_check
        CHECK (action IN ('publish', 'copy', 'apply_update', 'dismiss_update', 'import', 'export',
                          'delete', 'share', 'revoke_share', 'rename', 'git_push', 'archive'));
