WITH expected_privilege(table_name, privilege_type) AS (
    VALUES
        ('student', 'SELECT'),
        ('student', 'INSERT'),
        ('student', 'UPDATE'),
        ('account', 'SELECT'),
        ('account', 'INSERT'),
        ('account', 'UPDATE'),
        ('account_activation', 'SELECT'),
        ('account_activation', 'INSERT'),
        ('account_activation', 'UPDATE'),
        ('account_session', 'SELECT'),
        ('account_session', 'INSERT'),
        ('account_session', 'UPDATE'),
        ('account_session', 'DELETE'),
        ('daily_check_in', 'SELECT'),
        ('daily_check_in', 'INSERT'),
        ('user_points', 'SELECT'),
        ('user_points', 'INSERT'),
        ('user_points', 'UPDATE'),
        ('card_collection', 'SELECT'),
        ('card_collection', 'INSERT'),
        ('article_answer_keys', 'SELECT'),
        ('article_answer_keys', 'INSERT'),
        ('article_answer_keys', 'UPDATE'),
        ('article_answer_keys', 'DELETE'),
        ('article_question_submissions', 'SELECT'),
        ('article_question_submissions', 'INSERT'),
        ('article_question_submissions', 'UPDATE'),
        ('wrong_questions', 'SELECT'),
        ('wrong_questions', 'INSERT'),
        ('wrong_questions', 'UPDATE'),
        ('wrong_questions', 'DELETE')
), expected_sequence(sequence_name) AS (
    VALUES
        ('student_id_seq'),
        ('account_id_seq'),
        ('account_activation_id_seq'),
        ('account_session_id_seq'),
        ('daily_check_in_id_seq'),
        ('card_collection_id_seq'),
        ('article_answer_keys_id_seq'),
        ('article_question_submissions_id_seq'),
        ('wrong_questions_id_seq')
), expected_rls_table(table_name) AS (
    VALUES ('student'), ('account'), ('account_activation'), ('account_session'),
        ('daily_check_in'), ('user_points'), ('task_claims'), ('card_collection'),
        ('article_answer_keys'), ('article_question_submissions'), ('wrong_questions')
), expected_policy(table_name, policy_name, command, using_expression, check_expression) AS (
    VALUES
        ('student', 'tutor_base_runtime_student_select', 'r', 'true', NULL),
        ('student', 'tutor_base_runtime_student_insert', 'a', NULL, 'true'),
        ('student', 'tutor_base_runtime_student_update', 'w', 'true', 'true'),
        ('account', 'tutor_base_runtime_account_select', 'r', 'true', NULL),
        ('account', 'tutor_base_runtime_account_insert', 'a', NULL, 'true'),
        ('account', 'tutor_base_runtime_account_update', 'w', 'true', 'true'),
        ('account_activation', 'tutor_base_runtime_activation_select', 'r', 'true', NULL),
        ('account_activation', 'tutor_base_runtime_activation_insert', 'a', NULL, 'true'),
        ('account_activation', 'tutor_base_runtime_activation_update', 'w', 'true', 'true'),
        ('account_session', 'tutor_base_runtime_session_select', 'r', 'true', NULL),
        ('account_session', 'tutor_base_runtime_session_insert', 'a', NULL, 'true'),
        ('account_session', 'tutor_base_runtime_session_update', 'w', 'true', 'true'),
        ('account_session', 'tutor_base_runtime_session_delete', 'd', 'true', NULL),
        ('daily_check_in', 'tutor_base_runtime_check_in_select', 'r', 'true', NULL),
        ('daily_check_in', 'tutor_base_runtime_check_in_insert', 'a', NULL, 'true'),
        ('user_points', 'tutor_base_runtime_points_select', 'r', 'true', NULL),
        ('user_points', 'tutor_base_runtime_points_insert', 'a', NULL, 'true'),
        ('user_points', 'tutor_base_runtime_points_update', 'w', 'true', 'true'),
        ('card_collection', 'tutor_base_runtime_card_select', 'r', 'true', NULL),
        ('card_collection', 'tutor_base_runtime_card_insert', 'a', NULL, 'true'),
        ('article_answer_keys', 'tutor_base_runtime_answer_keys_all', '*', 'true', 'true'),
        ('article_question_submissions', 'tutor_base_runtime_submissions_all', '*', 'true', 'true'),
        ('wrong_questions', 'tutor_base_runtime_wrong_questions_all', '*', 'true', 'true')
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
    SELECT 'required sequence privileges missing', count(*)
    FROM expected_sequence
    WHERE NOT has_sequence_privilege(
        'tutor_base_app', format('public.%I', sequence_name), 'USAGE')

    UNION ALL
    SELECT 'dangerous table privileges granted', count(*)
    FROM (VALUES
        ('student', 'DELETE'), ('student', 'TRUNCATE'),
        ('account', 'DELETE'), ('account', 'TRUNCATE'),
        ('account_activation', 'DELETE'), ('account_activation', 'TRUNCATE'),
        ('account_session', 'TRUNCATE'),
        ('daily_check_in', 'UPDATE'), ('daily_check_in', 'DELETE'), ('daily_check_in', 'TRUNCATE'),
        ('user_points', 'DELETE'), ('user_points', 'TRUNCATE'),
        ('card_collection', 'UPDATE'), ('card_collection', 'DELETE'), ('card_collection', 'TRUNCATE'),
        ('article_answer_keys', 'TRUNCATE'),
        ('article_question_submissions', 'DELETE'), ('article_question_submissions', 'TRUNCATE'),
        ('wrong_questions', 'TRUNCATE')
    ) AS denied(table_name, privilege_type)
    WHERE has_table_privilege('tutor_base_app', format('public.%I', table_name), privilege_type)

    UNION ALL
    SELECT 'runtime RLS not enabled', count(*)
    FROM expected_rls_table
    JOIN pg_class table_class
      ON table_class.oid = format('public.%I', expected_rls_table.table_name)::regclass
    WHERE NOT table_class.relrowsecurity

    UNION ALL
    SELECT 'runtime RLS policies missing', count(*)
    FROM expected_policy
    LEFT JOIN pg_policy policy
      ON policy.polrelid = format('public.%I', expected_policy.table_name)::regclass
     AND policy.polname = expected_policy.policy_name
    LEFT JOIN pg_roles runtime_role ON runtime_role.rolname = 'tutor_base_runtime'
    WHERE policy.oid IS NULL
       OR policy.polcmd <> expected_policy.command
       OR NOT (runtime_role.oid = ANY (policy.polroles))
       OR pg_get_expr(policy.polqual, policy.polrelid)
            IS DISTINCT FROM expected_policy.using_expression
       OR pg_get_expr(policy.polwithcheck, policy.polrelid)
            IS DISTINCT FROM expected_policy.check_expression
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
