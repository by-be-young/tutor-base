-- 课件托管 P5 审计动作补 archive（幂等版，供已对齐的线上库重复执行）
-- 与 V25__courseware_log_archive_action.sql 等价。

ALTER TABLE public.course_operation_log
    DROP CONSTRAINT IF EXISTS course_operation_log_action_check;

ALTER TABLE public.course_operation_log
    ADD CONSTRAINT course_operation_log_action_check
        CHECK (action IN ('publish', 'copy', 'apply_update', 'dismiss_update', 'import', 'export',
                          'delete', 'share', 'revoke_share', 'rename', 'git_push', 'archive'));
