package com.tutorbase.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "student",
            "article_answer_keys",
            "article_question_submissions",
            "wrong_questions",
            "account",
            "account_activation",
            "account_session",
            "daily_check_in",
            "user_points",
            "task_claims",
            "card_collection",
            "achievement_claims",
            "task_assignments",
            "card_sets",
            "card_definitions",
            "courseware_catalog",
            "admin_subject_claim",
            "admin_courseware_claim",
            "admin_courseware_exclusion",
            "teacher_registration_application");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void givenEmptyPostgresWhenFlywayMigratesThenApplicationBaselineIsComplete() throws SQLException, IOException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();

        MigrateResult result = flyway.migrate();

        long expectedMigrations;
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            expectedMigrations = migrations
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .count();
        }
        assertThat(result.migrationsExecuted).isEqualTo((int) expectedMigrations);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18");
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(readApplicationTables(connection)).isEqualTo(EXPECTED_TABLES);
            assertThat(hasConstraint(connection, "article_question_submissions_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_student_source_unique")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_wrong_count_check")).isTrue();
            assertThat(hasConstraint(connection, "article_question_submissions_review_state_check")).isTrue();
            assertThat(isColumnNullable(connection, "student", "permissions")).isFalse();
            assertThat(hasColumn(connection, "student", "claimed_by")).isTrue();
            assertThat(hasColumn(connection, "student", "claimed_at")).isTrue();
            assertThat(hasColumn(connection, "student", "is_test")).isTrue();
            assertThat(hasColumn(connection, "student", "is_special_test")).isTrue();
            assertThat(isColumnNullable(connection, "student", "is_test")).isFalse();
            assertThat(isColumnNullable(connection, "student", "is_special_test")).isFalse();
            assertThat(hasConstraint(connection, "student_special_test_state_check")).isTrue();
            assertThat(hasConstraint(connection, "student_claimed_by_fkey")).isTrue();
            assertThat(hasConstraint(connection, "student_claim_state_check")).isTrue();
            assertThat(readCount(connection, """
                    SELECT count(*) FROM pg_trigger
                    WHERE tgrelid = 'public.student'::regclass
                      AND tgname = 'student_clear_claim_timestamp'
                      AND NOT tgisinternal
                    """)).isEqualTo(1);
            assertThat(hasConstraint(connection, "admin_subject_claim_admin_subject_unique")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_claim_admin_article_unique")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_exclusion_admin_article_unique")).isTrue();
            assertThat(hasConstraint(connection, "admin_subject_claim_admin_account_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_claim_article_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_exclusion_article_id_fkey")).isTrue();
            assertThat(isColumnNullable(connection, "courseware_catalog", "subject")).isFalse();
            assertThat(isColumnNullable(connection, "courseware_catalog", "title")).isTrue();
            assertThat(readCount(connection, """
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname IN (
                          'courseware_catalog_subject_idx',
                          'admin_subject_claim_subject_idx',
                          'admin_courseware_claim_article_idx',
                          'admin_courseware_exclusion_article_idx'
                      )
                    """)).isEqualTo(4);
            assertThat(readQuestionIdDataType(connection, "article_question_submissions")).isEqualTo("text");
            assertThat(hasConstraint(connection, "account_activation_state_check")).isTrue();
            assertThat(hasConstraint(connection, "account_activation_expiry_check")).isTrue();
            assertThat(hasColumn(connection, "account", "is_super_admin")).isTrue();
            assertThat(isColumnNullable(connection, "account", "is_super_admin")).isFalse();
            assertThat(hasConstraint(connection, "account_super_admin_role_check")).isTrue();
            assertThat(hasConstraint(connection, "teacher_registration_application_status_check")).isTrue();
            assertThat(hasConstraint(connection, "teacher_registration_application_state_check")).isTrue();
            assertThat(hasConstraint(connection, "teacher_registration_application_reason_length_check")).isTrue();
            assertThat(readCount(connection, """
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname = 'teacher_registration_application_pending_username'
                    """)).isEqualTo(1);
            assertThat(hasConstraint(connection, "daily_check_in_student_date_unique")).isTrue();
            assertThat(hasConstraint(connection, "daily_check_in_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "daily_check_in_points_awarded_check")).isTrue();
            assertThat(hasConstraint(connection, "daily_check_in_bonus_days_check")).isTrue();
            assertThat(isColumnNullable(connection, "daily_check_in", "bonus_days")).isTrue();
            assertThat(hasConstraint(connection, "user_points_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "task_claims_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "task_claims_student_task_unique")).isTrue();
            assertThat(hasConstraint(connection, "card_collection_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "card_collection_rarity_check")).isTrue();
            assertThat(hasColumn(connection, "card_collection", "milestone_points")).isFalse();
            assertThat(hasColumn(connection, "user_points", "lifetime_points")).isTrue();
            assertThat(hasColumn(connection, "user_points", "draw_count")).isTrue();
            assertThat(isColumnNullable(connection, "user_points", "draw_count")).isFalse();
            assertThat(readCount(connection, "SELECT count(*) FROM public.card_sets")).isEqualTo(10);
            assertThat(readCount(connection, "SELECT count(*) FROM public.card_definitions")).isEqualTo(70);
            assertThat(readCount(connection, """
                    SELECT count(*) FROM public.card_definitions
                    WHERE rarity = 'rare'
                    """)).isEqualTo(10);
            assertThat(readCount(connection, """
                    SELECT count(DISTINCT set_key) FROM public.card_definitions
                    """)).isEqualTo(10);

            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "post_v3_identity_checks.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "flyway_adoption_checks.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "flyway_adoption_details.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "operations", "provision_runtime_role.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "runtime_role_checks.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "operations", "revoke_browser_table_access.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "browser_table_access_checks.sql")));
            execute(connection, Files.readString(
                    Path.of("..", "database", "audit", "teacher_registration_checks.sql")));
            // reconciliation 必须可重复执行，且不会破坏 Flyway 已经建好的结构/种子数据。
            String gachaReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-20_gacha_card_pool.sql"));
            execute(connection, gachaReconciliation);
            execute(connection, gachaReconciliation);
            String learnerClaimingReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-27_learner_claiming.sql"));
            execute(connection, learnerClaimingReconciliation);
            execute(connection, learnerClaimingReconciliation);
            String coursewareClaimingReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-29_courseware_claiming.sql"));
            execute(connection, coursewareClaimingReconciliation);
            execute(connection, coursewareClaimingReconciliation);
            String coursewareExclusionReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-30_courseware_exclusion.sql"));
            execute(connection, coursewareExclusionReconciliation);
            execute(connection, coursewareExclusionReconciliation);
            String testLearnersReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-30_test_learners.sql"));
            execute(connection, testLearnersReconciliation);
            execute(connection, testLearnersReconciliation);
            String teacherReviewReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-31_teacher_registration_review.sql"));
            execute(connection, teacherReviewReconciliation);
            execute(connection, teacherReviewReconciliation);
            String studentSubjectsReconciliation = Files.readString(Path.of(
                    "..", "database", "reconciliation", "2026-10-31_student_subjects.sql"));
            execute(connection, studentSubjectsReconciliation);
            execute(connection, studentSubjectsReconciliation);
            assertThat(hasColumn(connection, "account", "is_super_admin")).isTrue();
            assertThat(hasConstraint(connection, "account_super_admin_role_check")).isTrue();
            assertThat(hasConstraint(connection, "teacher_registration_application_state_check")).isTrue();
            assertThat(hasColumn(connection, "student", "claimed_by")).isTrue();
            assertThat(hasColumn(connection, "student", "claimed_at")).isTrue();
            assertThat(hasColumn(connection, "student", "is_test")).isTrue();
            assertThat(hasColumn(connection, "student", "is_special_test")).isTrue();
            assertThat(hasColumn(connection, "student", "subjects")).isTrue();
            assertThat(isColumnNullable(connection, "student", "subjects")).isFalse();
            assertThat(hasConstraint(connection, "student_subjects_no_empty_check")).isTrue();
            assertThat(hasConstraint(connection, "student_special_test_state_check")).isTrue();
            assertThat(readCount(connection, """
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname = 'public' AND indexname = 'student_single_special_test_idx'
                    """)).isEqualTo(1);
            assertThat(hasConstraint(connection, "student_claimed_by_fkey")).isTrue();
            assertThat(hasConstraint(connection, "student_claim_state_check")).isTrue();
            assertThat(hasConstraint(connection, "admin_subject_claim_admin_subject_unique")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_claim_admin_article_unique")).isTrue();
            assertThat(hasConstraint(connection, "admin_courseware_exclusion_admin_article_unique")).isTrue();
            assertThat(readCount(connection, "SELECT count(*) FROM public.card_sets")).isEqualTo(10);
            assertThat(readCount(connection, "SELECT count(*) FROM public.card_definitions")).isEqualTo(70);
            assertThat(hasColumn(connection, "user_points", "draw_count")).isTrue();
            assertThat(hasColumn(connection, "card_collection", "milestone_points")).isFalse();
            assertThat(readCount(connection, """
                    SELECT count(*)
                    FROM pg_class AS table_class
                    JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND table_class.relname = ANY (ARRAY[
                          'student', 'account', 'account_activation', 'account_session',
                          'daily_check_in', 'user_points', 'task_claims', 'card_collection',
                          'article_answer_keys', 'article_question_submissions', 'wrong_questions',
                          'achievement_claims', 'task_assignments', 'card_sets', 'card_definitions',
                          'courseware_catalog', 'admin_subject_claim', 'admin_courseware_claim',
                          'admin_courseware_exclusion', 'teacher_registration_application'
                      ])
                      AND table_class.relrowsecurity
                    """)).isEqualTo(20);

            assertDatabaseRejectsInvalidStates(connection);
            assertWrongQuestionsFollowStudentLifecycle(connection);
            assertUpdatedAtTriggers(connection);
            assertClaimSurvivesAccountDeletion(connection);
            assertCoursewareExclusionIsUnique(connection);
        }
    }

    @Test
    void givenV11SchemaAppliedManuallyWhenFlywayReplaysV11ThenMigrationIsIdempotent() throws Exception {
        // 生产事故回归：先手工执行 reconciliation（schema 已含 V11/V12/V15/V16/V17/V18），但历史表停在 V10；
        // 新镜像启动时 Flyway 会再次执行 V11、V12、V14、V15、V16、V17、V18，必须成功记账而不是因对象已存在而失败。
        try (PostgreSQLContainer driftDatabase = new PostgreSQLContainer("postgres:17-alpine")) {
            driftDatabase.start();
            Flyway.configure()
                    .dataSource(driftDatabase.getJdbcUrl(), driftDatabase.getUsername(), driftDatabase.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("10"))
                    .load()
                    .migrate();

            // 复刻生产状态：V11/V12/V15/V16/V17/V18 的对象已手工建好，历史表仍停在 V10。
            try (Connection connection = driftDatabase.createConnection("")) {
                execute(connection, Files.readString(Path.of(
                        "..", "database", "reconciliation", "2026-10-27_learner_claiming.sql")));
                execute(connection, Files.readString(Path.of(
                        "..", "database", "reconciliation", "2026-10-29_courseware_claiming.sql")));
                execute(connection, Files.readString(Path.of(
                        "..", "database", "reconciliation", "2026-10-30_courseware_exclusion.sql")));
                execute(connection, Files.readString(Path.of(
                        "..", "database", "reconciliation", "2026-10-31_teacher_registration_review.sql")));
                execute(connection, Files.readString(Path.of(
                        "..", "database", "reconciliation", "2026-10-31_student_subjects.sql")));
                assertThat(readCount(connection,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE version = '11'")).isZero();
                assertThat(readCount(connection,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE version = '12'")).isZero();
                assertThat(readCount(connection,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE version = '15'")).isZero();
                assertThat(readCount(connection,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE version = '18'")).isZero();
            }

            MigrateResult result = Flyway.configure()
                    .dataSource(driftDatabase.getJdbcUrl(), driftDatabase.getUsername(), driftDatabase.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(result.migrationsExecuted).isEqualTo(7);
            try (Connection connection = driftDatabase.createConnection("")) {
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '11' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '12' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '14' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '15' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '16' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '17' AND success
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.flyway_schema_history
                        WHERE version = '18' AND success
                        """)).isEqualTo(1);
                assertThat(hasColumn(connection, "student", "subjects")).isTrue();
                assertThat(hasConstraint(connection, "student_subjects_no_empty_check")).isTrue();
                assertThat(hasColumn(connection, "account", "is_super_admin")).isTrue();
                assertThat(hasConstraint(connection, "account_super_admin_role_check")).isTrue();
                assertThat(hasConstraint(connection, "teacher_registration_application_state_check")).isTrue();
                assertThat(hasColumn(connection, "student", "claimed_by")).isTrue();
                assertThat(hasColumn(connection, "student", "claimed_at")).isTrue();
                assertThat(hasColumn(connection, "student", "is_test")).isTrue();
                assertThat(hasColumn(connection, "student", "is_special_test")).isTrue();
                assertThat(hasConstraint(connection, "student_special_test_state_check")).isTrue();
                assertThat(hasConstraint(connection, "student_claimed_by_fkey")).isTrue();
                assertThat(hasConstraint(connection, "student_claim_state_check")).isTrue();
                assertThat(hasConstraint(connection, "admin_subject_claim_admin_subject_unique")).isTrue();
                assertThat(hasConstraint(connection, "admin_courseware_claim_admin_article_unique")).isTrue();
                assertThat(hasConstraint(connection, "admin_courseware_exclusion_admin_article_unique")).isTrue();
                assertThat(readCount(connection, """
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'admin_courseware_exclusion_article_idx'
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM pg_trigger
                        WHERE tgrelid = 'public.student'::regclass
                          AND tgname = 'student_clear_claim_timestamp'
                          AND NOT tgisinternal
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'public' AND indexname = 'student_claimed_by_idx'
                        """)).isEqualTo(1);
            }
        }
    }

    @Test
    void givenPreV16LearnersWhenMigratingToV16ThenFlagsAndSpecialPermissionsAreBackfilled() throws Exception {
        try (PostgreSQLContainer preV16Database = new PostgreSQLContainer("postgres:17-alpine")) {
            preV16Database.start();
            Flyway.configure()
                    .dataSource(preV16Database.getJdbcUrl(), preV16Database.getUsername(),
                            preV16Database.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("12"))
                    .load()
                    .migrate();

            try (Connection connection = preV16Database.createConnection("")) {
                execute(connection, """
                        INSERT INTO public.student (id, username, permissions) VALUES
                            (1, 'pyt', '{}'::integer[]),
                            (2, 'Mel.', '{}'::integer[]),
                            (3, 'test', '{99}'::integer[]),
                            (4, 'alice', '{7}'::integer[]);
                        INSERT INTO public.courseware_catalog (article_id, subject)
                            VALUES (11, '数学'), (12, '数学');
                        """);
            }

            MigrateResult result = Flyway.configure()
                    .dataSource(preV16Database.getJdbcUrl(), preV16Database.getUsername(),
                            preV16Database.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(result.migrationsExecuted).isEqualTo(5);
            try (Connection connection = preV16Database.createConnection("")) {
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'pyt' AND NOT is_test AND NOT is_special_test
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'mel.' AND NOT is_test AND NOT is_special_test
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'test' AND is_test AND is_special_test
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'alice' AND is_test AND NOT is_special_test
                        """)).isEqualTo(1);
                // V18 从既有 permissions 反推科目；catalog 外的文章不产生科目。
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'test' AND subjects = '{数学}'::text[]
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'alice' AND subjects = '{}'::text[]
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'pyt' AND subjects = '{}'::text[]
                        """)).isEqualTo(1);
                // 特殊测试学员的 permissions 补齐为 catalog 全部 article_id（int8 -> int4）。
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'test' AND permissions = '{11,12}'::integer[]
                        """)).isEqualTo(1);
                assertThat(readCount(connection, """
                        SELECT count(*) FROM public.student
                        WHERE lower(username) = 'pyt' AND permissions = '{}'::integer[]
                        """)).isEqualTo(1);
                // 特殊测试学员必须同时是测试学员。
                assertSqlState(connection, "23514", """
                        INSERT INTO public.student (username, is_special_test)
                        VALUES ('special-only', true)
                        """);
                // 全局最多一个特殊测试学员。
                assertSqlState(connection, "23505", """
                        INSERT INTO public.student (username, is_test, is_special_test)
                        VALUES ('second-special', true, true)
                        """);
            }
        }
    }

    @Test
    void givenVerifiedProductionDriftWhenReconciledThenV2InvariantsAreInstalled() throws Exception {
        try (PostgreSQLContainer driftDatabase = new PostgreSQLContainer("postgres:17-alpine")) {
            driftDatabase.start();
            Flyway.configure()
                    .dataSource(driftDatabase.getJdbcUrl(), driftDatabase.getUsername(), driftDatabase.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("1"))
                    .load()
                    .migrate();

            try (Connection connection = driftDatabase.createConnection("")) {
                execute(connection, """
                        ALTER TABLE public.article_answer_keys
                            RENAME CONSTRAINT article_answer_keys_blog_question_unique TO unique_blog_question_id;
                        ALTER INDEX public.idx_article_answer_keys_question_id RENAME TO idx_answer_keys_question_id;
                        ALTER TABLE public.article_question_submissions
                            RENAME CONSTRAINT article_question_submissions_blog_student_question_unique
                            TO unique_blog_student_question_id;
                        ALTER TABLE public.article_question_submissions
                            ADD CONSTRAINT unique_submission UNIQUE (blog_id, student_id, question_id);
                        ALTER INDEX public.idx_article_question_submissions_question_id
                            RENAME TO idx_submissions_question_id;
                        ALTER TABLE public.wrong_questions
                            RENAME CONSTRAINT wrong_questions_student_source_unique
                            TO wrong_questions_student_id_source_blog_id_source_question_i_key;
                        """);

                String reconciliation = Files.readString(Path.of(
                        "..", "database", "reconciliation",
                        "2026-08-15_align_existing_schema_through_v3.sql"));
                execute(connection, reconciliation);
                execute(connection, reconciliation);

                assertThat(isColumnNullable(connection, "student", "permissions")).isFalse();
                assertThat(hasConstraint(connection, "article_question_submissions_review_state_check")).isTrue();
                assertThat(hasConstraint(connection, "wrong_questions_student_id_fkey")).isTrue();
                assertThat(hasConstraint(connection, "wrong_questions_wrong_count_check")).isTrue();
                assertThat(readCount(connection, """
                        SELECT count(*) FROM pg_proc AS procedure
                        JOIN pg_namespace AS namespace ON namespace.oid = procedure.pronamespace
                        WHERE namespace.nspname = 'public' AND procedure.proname = 'set_updated_at'
                        """)).isEqualTo(1);
                assertThat(readTriggerCount(connection)).isEqualTo(3);
            }
        }
    }

    @Test
    void givenV4SchemaWithoutOptionalRewardTablesWhenPreV5AuditRunsThenAuditCompletes() throws Exception {
        try (PostgreSQLContainer preV5Database = new PostgreSQLContainer("postgres:17-alpine")) {
            preV5Database.start();
            Flyway.configure()
                    .dataSource(preV5Database.getJdbcUrl(), preV5Database.getUsername(), preV5Database.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("4"))
                    .load()
                    .migrate();

            try (Connection connection = preV5Database.createConnection("")) {
                execute(connection, Files.readString(
                        Path.of("..", "database", "audit", "pre_v5_rewards_checks.sql")));
            }
        }
    }

    private void assertDatabaseRejectsInvalidStates(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO public.student (id, username) VALUES (100, 'constraint-test')");

        assertSqlState(connection, "23502", """
                INSERT INTO public.student (username, permissions)
                VALUES ('null-permissions', NULL)
                """);
        assertSqlState(connection, "23514", """
                INSERT INTO public.wrong_questions (student_id, wrong_count)
                VALUES (100, 0)
                """);
        assertSqlState(connection, "23514", """
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, review_status, review_result, reviewed_at)
                VALUES (1, 100, 'pending-with-review', 'pending', 'correct', now())
                """);
        assertSqlState(connection, "23514", """
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, review_status)
                VALUES (1, 100, 'reviewed-without-result', 'reviewed')
                """);
        assertSqlState(connection, "23502", """
                INSERT INTO public.teacher_registration_application
                    (username, username_normalized)
                VALUES ('no-hash', 'no-hash')
                """);
        assertSqlState(connection, "23514", """
                INSERT INTO public.teacher_registration_application
                    (username, username_normalized, password_hash, status)
                VALUES ('approved-no-account', 'approved-no-account', 'hash', 'approved')
                """);
        execute(connection, """
                INSERT INTO public.teacher_registration_application
                    (username, username_normalized, password_hash)
                VALUES ('pending-duplicate', 'pending-duplicate', 'hash')
                """);
        assertSqlState(connection, "23505", """
                INSERT INTO public.teacher_registration_application
                    (username, username_normalized, password_hash)
                VALUES ('PENDING-DUPLICATE', 'pending-duplicate', 'hash')
                """);
        execute(connection, """
                DELETE FROM public.teacher_registration_application
                WHERE username_normalized = 'pending-duplicate'
                """);
    }

    private void assertWrongQuestionsFollowStudentLifecycle(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO public.wrong_questions (id, student_id) VALUES (200, 100)");
        execute(connection, "DELETE FROM public.student WHERE id = 100");
        assertThat(readCount(connection, "SELECT count(*) FROM public.wrong_questions WHERE id = 200")).isZero();
    }

    private void assertUpdatedAtTriggers(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO public.student (id, username) VALUES (101, 'trigger-test');
                INSERT INTO public.article_answer_keys (id, blog_id, question_id, updated_at)
                    VALUES (301, 1, 'answer-key-trigger', '2000-01-01T00:00:00Z');
                INSERT INTO public.article_question_submissions
                    (id, blog_id, student_id, question_id, updated_at)
                    VALUES (302, 1, 101, 'submission-trigger', '2000-01-01T00:00:00Z');
                INSERT INTO public.wrong_questions (id, student_id, updated_at)
                    VALUES (303, 101, '2000-01-01T00:00:00Z');
                UPDATE public.article_answer_keys SET answer_text = 'updated' WHERE id = 301;
                UPDATE public.article_question_submissions SET answer_text = 'updated' WHERE id = 302;
                UPDATE public.wrong_questions SET note = 'updated' WHERE id = 303;
                """);

        OffsetDateTime oldTimestamp = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        assertThat(readUpdatedAt(connection, "article_answer_keys", 301)).isAfter(oldTimestamp);
        assertThat(readUpdatedAt(connection, "article_question_submissions", 302)).isAfter(oldTimestamp);
        assertThat(readUpdatedAt(connection, "wrong_questions", 303)).isAfter(oldTimestamp);
    }

    private void assertClaimSurvivesAccountDeletion(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO public.account (id, username, username_normalized, role)
                    VALUES (900, 'claim-admin', 'claim-admin', 'administrator');
                INSERT INTO public.student (id, username, claimed_by, claimed_at)
                    VALUES (901, 'claimed-learner', 900, now());
                DELETE FROM public.account WHERE id = 900;
                """);

        assertThat(readCount(connection, """
                SELECT count(*) FROM public.student
                WHERE id = 901 AND claimed_by IS NULL AND claimed_at IS NULL
                """)).isEqualTo(1);
        execute(connection, "DELETE FROM public.student WHERE id = 901");
    }

    private void assertCoursewareExclusionIsUnique(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO public.account (id, username, username_normalized, role)
                    VALUES (950, 'exclusion-admin', 'exclusion-admin', 'administrator');
                INSERT INTO public.courseware_catalog (article_id, subject)
                    VALUES (9501, '排除约束测试');
                INSERT INTO public.admin_courseware_exclusion (admin_account_id, article_id)
                    VALUES (950, 9501);
                """);

        assertSqlState(connection, "23505", """
                INSERT INTO public.admin_courseware_exclusion (admin_account_id, article_id)
                VALUES (950, 9501)
                """);
        assertThat(readCount(connection, """
                SELECT count(*) FROM public.admin_courseware_exclusion
                WHERE admin_account_id = 950 AND article_id = 9501
                """)).isEqualTo(1);

        execute(connection, """
                DELETE FROM public.admin_courseware_exclusion WHERE admin_account_id = 950;
                DELETE FROM public.courseware_catalog WHERE article_id = 9501;
                DELETE FROM public.account WHERE id = 950;
                """);
    }

    private Set<String> readApplicationTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                tables.add(rows.getString("table_name"));
            }
        }
        return tables;
    }

    private boolean hasConstraint(Connection connection, String constraintName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = 'public'
                      AND constraint_name = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, constraintName);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    private boolean isColumnNullable(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return "YES".equals(row.getString("is_nullable"));
            }
        }
    }

    private String readQuestionIdDataType(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = 'question_id'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getString("data_type");
            }
        }
    }

    private void assertSqlState(Connection connection, String expectedSqlState, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            assertThat(exception.getSQLState()).isEqualTo(expectedSqlState);
            return;
        }
        throw new AssertionError("Expected SQL to fail with SQLSTATE " + expectedSqlState);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long readCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getLong(1);
        }
    }

    private long readTriggerCount(Connection connection) throws SQLException {
        return readCount(connection, """
                SELECT count(*) FROM pg_trigger AS trigger
                JOIN pg_class AS table_class ON table_class.oid = trigger.tgrelid
                JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
                WHERE namespace.nspname = 'public'
                  AND trigger.tgname IN (
                      'article_answer_keys_set_updated_at',
                      'article_question_submissions_set_updated_at',
                      'wrong_questions_set_updated_at')
                  AND NOT trigger.tgisinternal
                """);
    }

    private OffsetDateTime readUpdatedAt(Connection connection, String tableName, long id) throws SQLException {
        String sql = "SELECT updated_at FROM public." + tableName + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getObject("updated_at", OffsetDateTime.class);
            }
        }
    }
}
