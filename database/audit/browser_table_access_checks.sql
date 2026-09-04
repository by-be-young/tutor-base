WITH application_table(table_name) AS (
    VALUES ('student'), ('account'), ('account_activation'), ('account_session'),
           ('article_answer_keys'), ('article_question_submissions'),
           ('wrong_questions'), ('daily_check_in'), ('user_points'),
           ('task_claims'), ('card_collection')
), application_sequence(sequence_name) AS (
    VALUES ('student_id_seq'), ('account_id_seq'), ('account_activation_id_seq'),
           ('account_session_id_seq'), ('daily_check_in_id_seq'),
           ('article_answer_keys_id_seq'), ('article_question_submissions_id_seq'),
           ('wrong_questions_id_seq'), ('task_claims_id_seq'), ('card_collection_id_seq')
), browser_role(role_name) AS (
    VALUES ('anon'), ('authenticated')
), privileges(privilege_type) AS (
    VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'), ('REFERENCES'), ('TRIGGER')
), checks AS (
    SELECT 'browser application-table privileges remain' AS check_name,
           count(*)::bigint AS anomaly_count
    FROM application_table CROSS JOIN browser_role CROSS JOIN privileges
    WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role.role_name)
      AND has_table_privilege(browser_role.role_name,
          format('public.%I', application_table.table_name), privileges.privilege_type)

    UNION ALL
    SELECT 'browser application-sequence privileges remain', count(*)::bigint
    FROM application_sequence CROSS JOIN browser_role
    CROSS JOIN (VALUES ('USAGE'), ('SELECT'), ('UPDATE')) sequence_privilege(privilege_type)
    WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role.role_name)
      AND has_sequence_privilege(browser_role.role_name,
          format('public.%I', application_sequence.sequence_name), sequence_privilege.privilege_type)
)
SELECT json_build_object(
    'checks', (SELECT json_agg(json_build_object(
        'check_name', check_name, 'anomaly_count', anomaly_count)) FROM checks)
) AS browser_access_report;
