-- 运行角色审计：一次性返回 JSON 报告，所有 anomaly_count 必须为 0。
-- 与 provision_runtime_role.sql 一样显式使用 public schema，避免执行上下文把 search_path
-- 解析到 pg_catalog 时误报“表不存在”。
SET search_path = public;

WITH expected_privilege(table_name, privilege_type) AS (
    VALUES
        ('student', 'SELECT'),
        ('student', 'INSERT'),
        ('student', 'UPDATE'),
        ('student', 'DELETE'),
        ('account', 'SELECT'),
        ('account', 'INSERT'),
        ('account', 'UPDATE'),
        ('account', 'DELETE'),
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
        ('wrong_questions', 'DELETE'),
        ('task_claims', 'SELECT'),
        ('task_claims', 'INSERT'),
        ('achievement_claims', 'SELECT'),
        ('achievement_claims', 'INSERT'),
        ('task_assignments', 'SELECT'),
        ('task_assignments', 'INSERT'),
        ('task_assignments', 'UPDATE'),
        ('card_sets', 'SELECT'),
        ('card_definitions', 'SELECT'),
        ('courseware_catalog', 'SELECT'),
        ('courseware_catalog', 'INSERT'),
        ('courseware_catalog', 'UPDATE'),
        ('courseware_catalog', 'DELETE'),
        ('admin_subject_claim', 'SELECT'),
        ('admin_subject_claim', 'INSERT'),
        ('admin_subject_claim', 'UPDATE'),
        ('admin_subject_claim', 'DELETE'),
        ('admin_courseware_claim', 'SELECT'),
        ('admin_courseware_claim', 'INSERT'),
        ('admin_courseware_claim', 'UPDATE'),
        ('admin_courseware_claim', 'DELETE'),
        ('admin_courseware_exclusion', 'SELECT'),
        ('admin_courseware_exclusion', 'INSERT'),
        ('admin_courseware_exclusion', 'UPDATE'),
        ('admin_courseware_exclusion', 'DELETE')
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
        ('wrong_questions_id_seq'),
        ('task_claims_id_seq'),
        ('achievement_claims_id_seq'),
        ('task_assignments_id_seq'),
        ('admin_subject_claim_id_seq'),
        ('admin_courseware_claim_id_seq'),
        ('admin_courseware_exclusion_id_seq')
), expected_rls_table(table_name) AS (
    VALUES ('student'), ('account'), ('account_activation'), ('account_session'),
        ('daily_check_in'), ('user_points'), ('task_claims'), ('card_collection'),
        ('article_answer_keys'), ('article_question_submissions'), ('wrong_questions'),
        ('achievement_claims'), ('task_assignments'), ('card_sets'), ('card_definitions'),
        ('courseware_catalog'), ('admin_subject_claim'), ('admin_courseware_claim'),
        ('admin_courseware_exclusion')
), expected_policy(table_name, policy_name, command, using_expression, check_expression) AS (
    VALUES
        ('student', 'tutor_base_runtime_student_select', 'r', 'true', NULL),
        ('student', 'tutor_base_runtime_student_insert', 'a', NULL, 'true'),
        ('student', 'tutor_base_runtime_student_update', 'w', 'true', 'true'),
        ('student', 'tutor_base_runtime_student_delete', 'd', 'true', NULL),
        ('account', 'tutor_base_runtime_account_select', 'r', 'true', NULL),
        ('account', 'tutor_base_runtime_account_insert', 'a', NULL, 'true'),
        ('account', 'tutor_base_runtime_account_update', 'w', 'true', 'true'),
        ('account', 'tutor_base_runtime_account_delete', 'd', 'true', NULL),
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
        ('wrong_questions', 'tutor_base_runtime_wrong_questions_all', '*', 'true', 'true'),
        ('task_claims', 'tutor_base_runtime_task_claims_all', '*', 'true', 'true'),
        ('achievement_claims', 'tutor_base_runtime_achievement_claims_all', '*', 'true', 'true'),
        ('task_assignments', 'tutor_base_runtime_task_assignments_all', '*', 'true', 'true'),
        ('card_sets', 'tutor_base_runtime_card_sets_select', 'r', 'true', NULL),
        ('card_definitions', 'tutor_base_runtime_card_definitions_select', 'r', 'true', NULL),
        ('courseware_catalog', 'tutor_base_runtime_courseware_catalog_all', '*', 'true', 'true'),
        ('admin_subject_claim', 'tutor_base_runtime_admin_subject_claim_all', '*', 'true', 'true'),
        ('admin_courseware_claim', 'tutor_base_runtime_admin_courseware_claim_all', '*', 'true', 'true'),
        ('admin_courseware_exclusion', 'tutor_base_runtime_admin_courseware_exclusion_all', '*', 'true', 'true')
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
        ('student', 'TRUNCATE'),
        ('account', 'TRUNCATE'),
        ('account_activation', 'DELETE'), ('account_activation', 'TRUNCATE'),
        ('account_session', 'TRUNCATE'),
        ('daily_check_in', 'UPDATE'), ('daily_check_in', 'DELETE'), ('daily_check_in', 'TRUNCATE'),
        ('user_points', 'DELETE'), ('user_points', 'TRUNCATE'),
        ('card_collection', 'UPDATE'), ('card_collection', 'DELETE'), ('card_collection', 'TRUNCATE'),
        ('article_answer_keys', 'TRUNCATE'),
        ('article_question_submissions', 'DELETE'), ('article_question_submissions', 'TRUNCATE'),
        ('wrong_questions', 'TRUNCATE'),
        ('card_sets', 'INSERT'), ('card_sets', 'UPDATE'), ('card_sets', 'DELETE'),
        ('card_sets', 'TRUNCATE'),
        ('card_definitions', 'INSERT'), ('card_definitions', 'UPDATE'), ('card_definitions', 'DELETE'),
        ('card_definitions', 'TRUNCATE')
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
