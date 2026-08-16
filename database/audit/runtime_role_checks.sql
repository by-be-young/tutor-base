WITH expected_privilege(table_name, privilege_type) AS (
    VALUES
        ('student', 'SELECT'),
        ('account', 'SELECT'),
        ('account', 'UPDATE'),
        ('account_activation', 'SELECT'),
        ('account_activation', 'INSERT'),
        ('account_activation', 'UPDATE'),
        ('account_session', 'SELECT'),
        ('account_session', 'INSERT'),
        ('account_session', 'UPDATE'),
        ('account_session', 'DELETE')
), expected_policy(table_name, policy_name) AS (
    VALUES
        ('account', 'tutor_base_runtime_account_select'),
        ('account', 'tutor_base_runtime_account_update'),
        ('account_activation', 'tutor_base_runtime_activation_select'),
        ('account_activation', 'tutor_base_runtime_activation_insert'),
        ('account_activation', 'tutor_base_runtime_activation_update'),
        ('account_session', 'tutor_base_runtime_session_select'),
        ('account_session', 'tutor_base_runtime_session_insert'),
        ('account_session', 'tutor_base_runtime_session_update'),
        ('account_session', 'tutor_base_runtime_session_delete')
), checks AS (
    SELECT 'runtime roles missing or unsafe' AS check_name, count(*)::bigint AS anomaly_count
    FROM (VALUES ('tutor_base_runtime'), ('tutor_base_app')) AS expected(role_name)
    LEFT JOIN pg_roles role ON role.rolname = expected.role_name
    WHERE role.oid IS NULL OR role.rolsuper OR role.rolcreatedb OR role.rolcreaterole
       OR role.rolreplication OR role.rolbypassrls

    UNION ALL
    SELECT 'runtime membership missing',
           CASE WHEN pg_has_role('tutor_base_app', 'tutor_base_runtime', 'member') THEN 0 ELSE 1 END

    UNION ALL
    SELECT 'runtime schema create unexpectedly granted',
           CASE WHEN has_schema_privilege('tutor_base_app', 'public', 'CREATE') THEN 1 ELSE 0 END

    UNION ALL
    SELECT 'required table privileges missing', count(*)
    FROM expected_privilege
    WHERE NOT has_table_privilege(
        'tutor_base_app', format('public.%I', table_name), privilege_type)

    UNION ALL
    SELECT 'dangerous table privileges granted', count(*)
    FROM (VALUES
        ('student', 'INSERT'), ('student', 'UPDATE'), ('student', 'DELETE'), ('student', 'TRUNCATE'),
        ('account', 'INSERT'), ('account', 'DELETE'), ('account', 'TRUNCATE'),
        ('account_activation', 'DELETE'), ('account_activation', 'TRUNCATE'),
        ('account_session', 'TRUNCATE'),
        ('article_answer_keys', 'SELECT'), ('article_answer_keys', 'INSERT'),
        ('article_question_submissions', 'SELECT'), ('article_question_submissions', 'INSERT'),
        ('wrong_questions', 'SELECT'), ('wrong_questions', 'INSERT')
    ) AS denied(table_name, privilege_type)
    WHERE has_table_privilege('tutor_base_app', format('public.%I', table_name), privilege_type)

    UNION ALL
    SELECT 'runtime RLS policies missing', count(*)
    FROM expected_policy
    LEFT JOIN pg_policy policy
      ON policy.polrelid = format('public.%I', expected_policy.table_name)::regclass
     AND policy.polname = expected_policy.policy_name
    WHERE policy.oid IS NULL
)
SELECT json_build_object(
    'checks', (SELECT json_agg(json_build_object(
        'check_name', check_name,
        'anomaly_count', anomaly_count
    ) ORDER BY check_name) FROM checks),
    'inventory', json_build_object(
        'runtime_role_exists', EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_runtime'),
        'login_role_exists', EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tutor_base_app'),
        'connection_limit', (SELECT rolconnlimit FROM pg_roles WHERE rolname = 'tutor_base_app')
    )
) AS runtime_role_report;
