-- Run this read-only report after V3 may have been applied manually.
-- Do not rerun V3 and do not delete objects to make the report pass.

BEGIN TRANSACTION READ ONLY;

SELECT jsonb_build_object(
    'checks', jsonb_build_array(
        jsonb_build_object(
            'check_name', 'student normalized username duplicate',
            'anomaly_count', (
                SELECT count(*)
                FROM (
                    SELECT lower(btrim(username))
                    FROM public.student
                    GROUP BY lower(btrim(username))
                    HAVING count(*) > 1
                ) AS duplicates
            )
        ),
        jsonb_build_object(
            'check_name', 'student blank normalized username',
            'anomaly_count', (
                SELECT count(*) FROM public.student WHERE btrim(username) = ''
            )
        ),
        jsonb_build_object(
            'check_name', 'identity tables present',
            'anomaly_count', 3 - (
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('account', 'account_activation', 'account_session')
            )
        ),
        jsonb_build_object(
            'check_name', 'account columns missing',
            'anomaly_count', (
                SELECT count(*)
                FROM (VALUES
                    ('id'), ('learner_id'), ('username'), ('username_normalized'),
                    ('password_hash'), ('status'), ('role'), ('created_at'), ('activated_at')
                ) AS expected(column_name)
                LEFT JOIN information_schema.columns AS actual
                  ON actual.table_schema = 'public'
                 AND actual.table_name = 'account'
                 AND actual.column_name = expected.column_name
                WHERE actual.column_name IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'identity constraints missing',
            'anomaly_count', (
                SELECT count(*)
                FROM (VALUES
                    ('account_username_trimmed_check'),
                    ('account_username_normalized_check'),
                    ('account_status_check'),
                    ('account_role_check'),
                    ('account_activation_state_check'),
                    ('account_activation_expiry_check'),
                    ('account_activation_terminal_state_check')
                ) AS expected(constraint_name)
                LEFT JOIN information_schema.table_constraints AS actual
                  ON actual.constraint_schema = 'public'
                 AND actual.constraint_name = expected.constraint_name
                WHERE actual.constraint_name IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'identity indexes missing',
            'anomaly_count', (
                SELECT count(*)
                FROM (VALUES
                    ('account_username_normalized_unique'),
                    ('account_activation_account_id_idx'),
                    ('account_activation_one_live_per_account'),
                    ('account_session_account_id_idx'),
                    ('account_session_expires_at_idx')
                ) AS expected(index_name)
                LEFT JOIN pg_indexes AS actual
                  ON actual.schemaname = 'public'
                 AND actual.indexname = expected.index_name
                WHERE actual.indexname IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'student account backfill mismatch',
            'anomaly_count', (
                SELECT count(*)
                FROM public.student AS student
                LEFT JOIN public.account AS account ON account.learner_id = student.id
                WHERE account.id IS NULL
                   OR account.id <> student.id
                   OR account.username <> btrim(student.username)
                   OR account.username_normalized <> lower(btrim(student.username))
            )
        ),
        jsonb_build_object(
            'check_name', 'young administrator backfill mismatch',
            'anomaly_count', (
                SELECT count(*)
                FROM public.account
                WHERE username_normalized = 'young'
                  AND role <> 'administrator'
            )
        )
    ),
    'inventory', jsonb_build_object(
        'student_rows', (SELECT count(*) FROM public.student),
        'account_rows', (SELECT count(*) FROM public.account),
        'activation_rows', (SELECT count(*) FROM public.account_activation),
        'session_rows', (SELECT count(*) FROM public.account_session),
        'flyway_history_exists', to_regclass('public.flyway_schema_history') IS NOT NULL
    )
) AS post_v3_report;

ROLLBACK;
