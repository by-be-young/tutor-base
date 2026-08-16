-- Read-only gate before adopting an existing Supabase schema as Flyway baseline V3.
-- Run only after post_v3_identity_checks.sql passes. Every anomaly_count must be 0.

BEGIN TRANSACTION READ ONLY;

SELECT jsonb_build_object(
    'checks', jsonb_build_array(
        jsonb_build_object(
            'check_name', 'application tables missing',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    ('student'), ('article_answer_keys'), ('article_question_submissions'),
                    ('wrong_questions'), ('account'), ('account_activation'), ('account_session')
                ) AS expected(table_name)
                LEFT JOIN information_schema.tables AS actual
                  ON actual.table_schema = 'public' AND actual.table_name = expected.table_name
                WHERE actual.table_name IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'V1/V2 columns missing or incompatible',
            'anomaly_count', (
                SELECT count(*)
                FROM (VALUES
                    ('student', 'id', 'int8', 'NO'),
                    ('student', 'username', 'text', 'NO'),
                    ('student', 'permissions', '_int4', 'NO'),
                    ('article_answer_keys', 'id', 'int8', 'NO'),
                    ('article_answer_keys', 'blog_id', 'int8', 'NO'),
                    ('article_answer_keys', 'answer_text', 'text', 'NO'),
                    ('article_answer_keys', 'auto_grade', 'bool', 'NO'),
                    ('article_answer_keys', 'created_at', 'timestamptz', 'NO'),
                    ('article_answer_keys', 'updated_at', 'timestamptz', 'NO'),
                    ('article_answer_keys', 'question_id', 'text', 'NO'),
                    ('article_question_submissions', 'id', 'int8', 'NO'),
                    ('article_question_submissions', 'blog_id', 'int8', 'NO'),
                    ('article_question_submissions', 'student_id', 'int8', 'NO'),
                    ('article_question_submissions', 'answer_text', 'text', 'NO'),
                    ('article_question_submissions', 'review_status', 'text', 'NO'),
                    ('article_question_submissions', 'review_result', 'text', 'YES'),
                    ('article_question_submissions', 'submitted_at', 'timestamptz', 'NO'),
                    ('article_question_submissions', 'reviewed_at', 'timestamptz', 'YES'),
                    ('article_question_submissions', 'updated_at', 'timestamptz', 'NO'),
                    ('article_question_submissions', 'question_id', 'text', 'NO'),
                    ('wrong_questions', 'id', 'int8', 'NO'),
                    ('wrong_questions', 'student_id', 'int8', 'NO'),
                    ('wrong_questions', 'correct_answer', 'text', 'NO'),
                    ('wrong_questions', 'my_answer', 'text', 'NO'),
                    ('wrong_questions', 'wrong_reason', 'text', 'NO'),
                    ('wrong_questions', 'tags', '_text', 'NO'),
                    ('wrong_questions', 'note', 'text', 'NO'),
                    ('wrong_questions', 'source_blog_id', 'int8', 'YES'),
                    ('wrong_questions', 'source_question_id', 'text', 'YES'),
                    ('wrong_questions', 'is_manual', 'bool', 'NO'),
                    ('wrong_questions', 'mastered', 'bool', 'NO'),
                    ('wrong_questions', 'removed', 'bool', 'NO'),
                    ('wrong_questions', 'wrong_count', 'int4', 'NO'),
                    ('wrong_questions', 'created_at', 'timestamptz', 'NO'),
                    ('wrong_questions', 'updated_at', 'timestamptz', 'NO')
                ) AS expected(table_name, column_name, udt_name, is_nullable)
                LEFT JOIN information_schema.columns AS actual
                  ON actual.table_schema = 'public'
                 AND actual.table_name = expected.table_name
                 AND actual.column_name = expected.column_name
                WHERE actual.column_name IS NULL
                   OR actual.udt_name <> expected.udt_name
                   OR actual.is_nullable <> expected.is_nullable
            )
        ),
        jsonb_build_object(
            'check_name', 'V1/V2 constraints missing',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    ('student_pkey'), ('student_username_key'),
                    ('article_answer_keys_pkey'),
                    ('article_question_submissions_pkey'),
                    ('article_question_submissions_review_status_check'),
                    ('article_question_submissions_review_result_check'),
                    ('article_question_submissions_student_id_fkey'),
                    ('article_question_submissions_review_state_check'),
                    ('wrong_questions_pkey'),
                    ('wrong_questions_student_id_fkey'), ('wrong_questions_wrong_count_check')
                ) AS expected(constraint_name)
                LEFT JOIN information_schema.table_constraints AS actual
                  ON actual.constraint_schema = 'public'
                 AND actual.constraint_name = expected.constraint_name
                WHERE actual.constraint_name IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'business unique constraints missing',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    ('article_answer_keys', 'UNIQUE (blog_id, question_id)'),
                    ('article_question_submissions', 'UNIQUE (blog_id, student_id, question_id)'),
                    ('wrong_questions', 'UNIQUE (student_id, source_blog_id, source_question_id)')
                ) AS expected(table_name, definition)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM pg_constraint AS constraint_row
                    WHERE constraint_row.conrelid = format('public.%I', expected.table_name)::regclass
                      AND constraint_row.contype = 'u'
                      AND pg_get_constraintdef(constraint_row.oid, true) = expected.definition
                )
            )
        ),
        jsonb_build_object(
            'check_name', 'V1 indexes missing',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    (ARRAY['idx_article_answer_keys_blog_id']),
                    (ARRAY['idx_article_answer_keys_question_id', 'idx_answer_keys_question_id']),
                    (ARRAY['idx_article_question_submissions_blog_student']),
                    (ARRAY['idx_article_question_submissions_question_id', 'idx_submissions_question_id']),
                    (ARRAY['idx_article_question_submissions_review_status'])
                ) AS expected(accepted_names)
                LEFT JOIN pg_indexes AS actual
                  ON actual.schemaname = 'public' AND actual.indexname = ANY(expected.accepted_names)
                WHERE actual.indexname IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'V2 updated_at function missing',
            'anomaly_count', CASE
                WHEN to_regprocedure('public.set_updated_at()') IS NULL THEN 1 ELSE 0 END
        ),
        jsonb_build_object(
            'check_name', 'V2 updated_at triggers missing or incompatible',
            'anomaly_count', (
                SELECT count(*)
                FROM (VALUES
                    ('article_answer_keys', 'article_answer_keys_set_updated_at'),
                    ('article_question_submissions', 'article_question_submissions_set_updated_at'),
                    ('wrong_questions', 'wrong_questions_set_updated_at')
                ) AS expected(table_name, trigger_name)
                LEFT JOIN (
                    SELECT table_class.relname AS table_name,
                           trigger.tgname AS trigger_name,
                           procedure.proname AS procedure_name,
                           trigger.tgenabled
                    FROM pg_trigger AS trigger
                    JOIN pg_class AS table_class ON table_class.oid = trigger.tgrelid
                    JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
                    JOIN pg_proc AS procedure ON procedure.oid = trigger.tgfoid
                    WHERE namespace.nspname = 'public' AND NOT trigger.tgisinternal
                ) AS actual
                  ON actual.table_name = expected.table_name
                 AND actual.trigger_name = expected.trigger_name
                WHERE actual.trigger_name IS NULL
                   OR actual.procedure_name <> 'set_updated_at'
                   OR actual.tgenabled = 'D'
            )
        ),
        jsonb_build_object(
            'check_name', 'student foreign keys are not cascade delete',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    ('article_question_submissions_student_id_fkey'),
                    ('wrong_questions_student_id_fkey')
                ) AS expected(constraint_name)
                LEFT JOIN information_schema.referential_constraints AS actual
                  ON actual.constraint_schema = 'public'
                 AND actual.constraint_name = expected.constraint_name
                WHERE actual.constraint_name IS NULL OR actual.delete_rule <> 'CASCADE'
            )
        ),
        jsonb_build_object(
            'check_name', 'identity generators missing',
            'anomaly_count', (
                SELECT count(*) FROM (VALUES
                    ('student'), ('article_answer_keys'), ('article_question_submissions'),
                    ('wrong_questions'), ('account'), ('account_activation'), ('account_session')
                ) AS expected(table_name)
                WHERE pg_get_serial_sequence(
                    format('%I.%I', 'public', expected.table_name), 'id') IS NULL
            )
        ),
        jsonb_build_object(
            'check_name', 'existing Flyway history before adoption',
            'anomaly_count', CASE
                WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 0 ELSE 1 END
        )
    ),
    'inventory', jsonb_build_object(
        'student_rows', (SELECT count(*) FROM public.student),
        'answer_key_rows', (SELECT count(*) FROM public.article_answer_keys),
        'submission_rows', (SELECT count(*) FROM public.article_question_submissions),
        'wrong_question_rows', (SELECT count(*) FROM public.wrong_questions),
        'account_rows', (SELECT count(*) FROM public.account),
        'database_version', current_setting('server_version')
    )
) AS flyway_adoption_report;

ROLLBACK;
