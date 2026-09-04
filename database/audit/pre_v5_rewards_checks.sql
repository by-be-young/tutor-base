DROP TABLE IF EXISTS pg_temp.pre_v5_rewards_checks;

CREATE TEMPORARY TABLE pre_v5_rewards_checks (
    check_name text PRIMARY KEY,
    anomaly_count bigint NOT NULL
);

INSERT INTO pre_v5_rewards_checks
SELECT 'required reward tables missing',
       CASE WHEN to_regclass('public.user_points') IS NULL THEN 1 ELSE 0 END;

INSERT INTO pre_v5_rewards_checks
SELECT 'existing reward columns missing or incompatible', count(*)
FROM (VALUES
    ('user_points', 'student_id', 'int8', 'NO', false),
    ('user_points', 'points', 'int8', 'NO', true),
    ('user_points', 'updated_at', 'timestamptz', 'NO', true),
    ('task_claims', 'id', 'int8', 'NO', true),
    ('task_claims', 'student_id', 'int8', 'NO', false),
    ('task_claims', 'task_id', 'int8', 'NO', false),
    ('task_claims', 'claimed_at', 'timestamptz', 'NO', true),
    ('card_collection', 'id', 'int8', 'NO', true),
    ('card_collection', 'student_id', 'int8', 'NO', false),
    ('card_collection', 'milestone_points', 'int8', 'NO', false),
    ('card_collection', 'card_key', 'text', 'NO', false),
    ('card_collection', 'set_key', 'text', 'NO', false),
    ('card_collection', 'rarity', 'text', 'NO', false),
    ('card_collection', 'claimed_at', 'timestamptz', 'NO', true)
) expected(table_name, column_name, udt_name, is_nullable, generated_value_required)
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = 'public'
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
WHERE to_regclass(format('public.%I', expected.table_name)) IS NOT NULL
  AND (
      actual.column_name IS NULL
      OR actual.udt_name <> expected.udt_name
      OR actual.is_nullable <> expected.is_nullable
      OR (expected.generated_value_required
          AND actual.is_identity <> 'YES'
          AND actual.column_default IS NULL)
  );

DO $audit$
BEGIN
    IF to_regclass('public.user_points') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM (VALUES ('student_id', 'int8'), ('points', 'int8')) expected(column_name, udt_name)
           LEFT JOIN information_schema.columns actual
             ON actual.table_schema = 'public'
            AND actual.table_name = 'user_points'
            AND actual.column_name = expected.column_name
           WHERE actual.column_name IS NULL OR actual.udt_name <> expected.udt_name
       ) THEN
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'user_points orphan learner', count(*)
            FROM public.user_points points
            LEFT JOIN public.student learner ON learner.id = points.student_id
            WHERE learner.id IS NULL
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'negative user points', count(*)
            FROM public.user_points
            WHERE points < 0
        $query$;
    END IF;

    IF to_regclass('public.task_claims') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM (VALUES ('student_id', 'int8'), ('task_id', 'int8')) expected(column_name, udt_name)
           LEFT JOIN information_schema.columns actual
             ON actual.table_schema = 'public'
            AND actual.table_name = 'task_claims'
            AND actual.column_name = expected.column_name
           WHERE actual.column_name IS NULL OR actual.udt_name <> expected.udt_name
       ) THEN
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'task_claims orphan learner', count(*)
            FROM public.task_claims claim
            LEFT JOIN public.student learner ON learner.id = claim.student_id
            WHERE learner.id IS NULL
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'task_claims duplicate business key', count(*)
            FROM (
                SELECT student_id, task_id
                FROM public.task_claims
                GROUP BY student_id, task_id
                HAVING count(*) > 1
            ) duplicate
        $query$;
    END IF;

    IF to_regclass('public.card_collection') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM (VALUES
               ('student_id', 'int8'), ('milestone_points', 'int8'), ('rarity', 'text')
           ) expected(column_name, udt_name)
           LEFT JOIN information_schema.columns actual
             ON actual.table_schema = 'public'
            AND actual.table_name = 'card_collection'
            AND actual.column_name = expected.column_name
           WHERE actual.column_name IS NULL OR actual.udt_name <> expected.udt_name
       ) THEN
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'card_collection orphan learner', count(*)
            FROM public.card_collection card
            LEFT JOIN public.student learner ON learner.id = card.student_id
            WHERE learner.id IS NULL
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'card_collection duplicate business key', count(*)
            FROM (
                SELECT student_id, milestone_points
                FROM public.card_collection
                GROUP BY student_id, milestone_points
                HAVING count(*) > 1
            ) duplicate
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v5_rewards_checks
            SELECT 'invalid card milestone or rarity', count(*)
            FROM public.card_collection
            WHERE milestone_points <= 0 OR rarity NOT IN ('common', 'rare')
        $query$;
    END IF;
END
$audit$;

WITH checks AS (
    SELECT check_name, anomaly_count
    FROM pre_v5_rewards_checks
)
SELECT json_build_object(
    'checks', (SELECT json_agg(json_build_object(
        'check_name', check_name, 'anomaly_count', anomaly_count) ORDER BY check_name) FROM checks),
    'inventory', json_build_object(
        'user_points_exists', to_regclass('public.user_points') IS NOT NULL,
        'task_claims_exists', to_regclass('public.task_claims') IS NOT NULL,
        'card_collection_exists', to_regclass('public.card_collection') IS NOT NULL
    )
) AS pre_v5_rewards_report;

DROP TABLE pg_temp.pre_v5_rewards_checks;
