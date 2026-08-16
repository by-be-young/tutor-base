-- Read-only catalog details for explaining non-zero flyway_adoption_checks.sql results.

BEGIN TRANSACTION READ ONLY;

SELECT jsonb_build_object(
    'columns', (
        SELECT jsonb_agg(jsonb_build_object(
            'table_name', table_name,
            'column_name', column_name,
            'udt_name', udt_name,
            'is_nullable', is_nullable,
            'column_default', column_default,
            'is_identity', is_identity
        ) ORDER BY table_name, ordinal_position)
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN (
              'student', 'article_answer_keys',
              'article_question_submissions', 'wrong_questions')
    ),
    'constraints', (
        SELECT jsonb_agg(jsonb_build_object(
            'table_name', table_class.relname,
            'constraint_name', constraint_row.conname,
            'constraint_type', constraint_row.contype,
            'definition', pg_get_constraintdef(constraint_row.oid, true)
        ) ORDER BY table_class.relname, constraint_row.conname)
        FROM pg_constraint AS constraint_row
        JOIN pg_class AS table_class ON table_class.oid = constraint_row.conrelid
        JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
        WHERE namespace.nspname = 'public'
          AND table_class.relname IN (
              'student', 'article_answer_keys',
              'article_question_submissions', 'wrong_questions')
    ),
    'indexes', (
        SELECT jsonb_agg(jsonb_build_object(
            'table_name', tablename,
            'index_name', indexname,
            'definition', indexdef
        ) ORDER BY tablename, indexname)
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename IN (
              'student', 'article_answer_keys',
              'article_question_submissions', 'wrong_questions')
    ),
    'triggers', (
        SELECT coalesce(jsonb_agg(jsonb_build_object(
            'table_name', event_object_table,
            'trigger_name', trigger_name,
            'action_timing', action_timing,
            'event_manipulation', event_manipulation,
            'action_statement', action_statement
        ) ORDER BY event_object_table, trigger_name), '[]'::jsonb)
        FROM information_schema.triggers
        WHERE trigger_schema = 'public'
          AND event_object_table IN (
              'article_answer_keys', 'article_question_submissions', 'wrong_questions')
    ),
    'set_updated_at_function_exists',
        to_regprocedure('public.set_updated_at()') IS NOT NULL
) AS flyway_adoption_details;

ROLLBACK;
