DROP TABLE IF EXISTS pg_temp.pre_v4_daily_checkin_checks;

CREATE TEMPORARY TABLE pre_v4_daily_checkin_checks (
    check_name text PRIMARY KEY,
    anomaly_count bigint NOT NULL
);

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in table missing',
       CASE WHEN to_regclass('public.daily_check_in') IS NULL THEN 1 ELSE 0 END;

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in columns missing or incompatible', count(*)
FROM (VALUES
    ('id', 'int8', 'NO', true),
    ('student_id', 'int8', 'NO', false),
    ('check_in_date', 'date', 'NO', false),
    ('points_awarded', 'int8', 'NO', false),
    ('bonus_days', 'int2', 'YES', false),
    ('created_at', 'timestamptz', 'NO', true)
) expected(column_name, udt_name, is_nullable, generated_value_required)
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = 'public'
 AND actual.table_name = 'daily_check_in'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.udt_name <> expected.udt_name
   OR actual.is_nullable <> expected.is_nullable
   OR (expected.generated_value_required
       AND actual.is_identity <> 'YES'
       AND actual.column_default IS NULL);

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in unexpected columns', count(*)
FROM information_schema.columns actual
WHERE actual.table_schema = 'public'
  AND actual.table_name = 'daily_check_in'
  AND actual.column_name NOT IN (
      'id', 'student_id', 'check_in_date', 'points_awarded', 'bonus_days', 'created_at'
  );

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in primary key missing',
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = to_regclass('public.daily_check_in')
             AND contype = 'p'
             AND pg_get_constraintdef(oid) = 'PRIMARY KEY (id)'
       ) THEN 0 ELSE 1 END;

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in learner cascade foreign key missing',
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = to_regclass('public.daily_check_in')
             AND contype = 'f'
             AND pg_get_constraintdef(oid) =
                 'FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE'
       ) THEN 0 ELSE 1 END;

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in business unique constraint missing',
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = to_regclass('public.daily_check_in')
             AND contype = 'u'
             AND pg_get_constraintdef(oid) = 'UNIQUE (student_id, check_in_date)'
       ) THEN 0 ELSE 1 END;

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in check constraints missing',
       2 - count(*)
FROM pg_constraint
WHERE conrelid = to_regclass('public.daily_check_in')
  AND contype = 'c'
  AND pg_get_constraintdef(oid) IN (
      'CHECK ((points_awarded > 0))',
      'CHECK ((bonus_days = ANY (ARRAY[7, 14])))'
  );

INSERT INTO pre_v4_daily_checkin_checks
SELECT 'daily check-in lookup index missing',
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_indexes
           WHERE schemaname = 'public'
             AND tablename = 'daily_check_in'
             AND indexdef LIKE '%(student_id, check_in_date DESC)%'
       ) THEN 0 ELSE 1 END;

DO $audit$
BEGIN
    IF to_regclass('public.daily_check_in') IS NOT NULL THEN
        EXECUTE $query$
            INSERT INTO pre_v4_daily_checkin_checks
            SELECT 'daily check-in orphan learner', count(*)
            FROM public.daily_check_in checkin
            LEFT JOIN public.student learner ON learner.id = checkin.student_id
            WHERE learner.id IS NULL
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v4_daily_checkin_checks
            SELECT 'daily check-in duplicate business key', count(*)
            FROM (
                SELECT student_id, check_in_date
                FROM public.daily_check_in
                GROUP BY student_id, check_in_date
                HAVING count(*) > 1
            ) duplicate
        $query$;
        EXECUTE $query$
            INSERT INTO pre_v4_daily_checkin_checks
            SELECT 'daily check-in invalid reward data', count(*)
            FROM public.daily_check_in
            WHERE points_awarded <= 0
               OR (bonus_days IS NOT NULL AND bonus_days NOT IN (7, 14))
        $query$;
    END IF;
END
$audit$;

WITH checks AS (
    SELECT check_name, anomaly_count
    FROM pre_v4_daily_checkin_checks
), history AS (
    SELECT coalesce(max(version), 'none') AS current_version,
           count(*) FILTER (WHERE success) AS successful_rows
    FROM public.flyway_schema_history
)
SELECT json_build_object(
    'checks', (SELECT json_agg(json_build_object(
        'check_name', check_name,
        'anomaly_count', anomaly_count
    ) ORDER BY check_name) FROM checks),
    'inventory', json_build_object(
        'daily_check_in_rows', CASE
            WHEN to_regclass('public.daily_check_in') IS NULL THEN NULL
            ELSE (SELECT count(*) FROM public.daily_check_in)
        END,
        'flyway_current_version', (SELECT current_version FROM history),
        'flyway_successful_rows', (SELECT successful_rows FROM history)
    )
) AS pre_v4_daily_checkin_report;

DROP TABLE pg_temp.pre_v4_daily_checkin_checks;
