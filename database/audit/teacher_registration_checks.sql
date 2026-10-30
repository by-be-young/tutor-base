-- 教师注册审核（V17）审计报告：所有 anomaly_count 必须为 0。
SET search_path = public;

WITH expected_table(table_name) AS (
    VALUES ('teacher_registration_application')
), expected_index(index_name) AS (
    VALUES
        ('teacher_registration_application_pending_username'),
        ('teacher_registration_application_status_created_idx'),
        ('teacher_registration_application_reviewed_by_idx')
), expected_constraint(constraint_name) AS (
    VALUES
        ('account_super_admin_role_check'),
        ('teacher_registration_application_username_trimmed_check'),
        ('teacher_registration_application_username_normalized_check'),
        ('teacher_registration_application_status_check'),
        ('teacher_registration_application_reason_length_check'),
        ('teacher_registration_application_state_check')
), checks AS (
    SELECT 'teacher registration tables missing' AS check_name, count(*)::bigint AS anomaly_count
    FROM expected_table
    LEFT JOIN information_schema.tables actual
      ON actual.table_schema = 'public' AND actual.table_name = expected_table.table_name
    WHERE actual.table_name IS NULL

    UNION ALL
    SELECT 'teacher registration indexes missing', count(*)
    FROM expected_index
    LEFT JOIN pg_indexes actual
      ON actual.schemaname = 'public' AND actual.indexname = expected_index.index_name
    WHERE actual.indexname IS NULL

    UNION ALL
    SELECT 'teacher registration constraints missing', count(*)
    FROM expected_constraint
    LEFT JOIN information_schema.table_constraints actual
      ON actual.constraint_schema = 'public' AND actual.constraint_name = expected_constraint.constraint_name
    WHERE actual.constraint_name IS NULL

    UNION ALL
    SELECT 'super admin flag missing on young', count(*)
    FROM public.account
    WHERE username_normalized = 'young'
      AND role = 'administrator'
      AND NOT is_super_admin

    UNION ALL
    SELECT 'multiple super administrators', GREATEST(count(*) - 1, 0)
    FROM public.account
    WHERE is_super_admin

    UNION ALL
    SELECT 'approval without account projection', count(*)
    FROM public.teacher_registration_application
    WHERE status = 'approved' AND approved_account_id IS NULL

    UNION ALL
    SELECT 'pending approval retained reviewer state', count(*)
    FROM public.teacher_registration_application
    WHERE status = 'pending'
      AND (reviewed_at IS NOT NULL OR reviewed_by IS NOT NULL
           OR rejection_reason IS NOT NULL OR approved_account_id IS NOT NULL)
)
SELECT json_build_object(
    'checks', (SELECT json_agg(json_build_object(
        'check_name', check_name, 'anomaly_count', anomaly_count)) FROM checks),
    'inventory', json_build_object(
        'teacher_application_rows', (SELECT count(*) FROM public.teacher_registration_application),
        'pending_rows', (SELECT count(*) FROM public.teacher_registration_application WHERE status = 'pending'),
        'super_admin_rows', (SELECT count(*) FROM public.account WHERE is_super_admin)
    )
) AS teacher_registration_report;
