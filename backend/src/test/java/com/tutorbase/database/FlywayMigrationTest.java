package com.tutorbase.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "student",
            "article_answer_keys",
            "article_question_submissions",
            "wrong_questions",
            "account",
            "account_activation",
            "account_session");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void givenEmptyPostgresWhenFlywayMigratesThenApplicationBaselineIsComplete() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(readApplicationTables(connection)).isEqualTo(EXPECTED_TABLES);
            assertThat(hasConstraint(connection, "article_question_submissions_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_student_source_unique")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_student_id_fkey")).isTrue();
            assertThat(hasConstraint(connection, "wrong_questions_wrong_count_check")).isTrue();
            assertThat(hasConstraint(connection, "article_question_submissions_review_state_check")).isTrue();
            assertThat(isColumnNullable(connection, "student", "permissions")).isFalse();
            assertThat(readQuestionIdDataType(connection, "article_question_submissions")).isEqualTo("text");
            assertThat(hasConstraint(connection, "account_activation_state_check")).isTrue();
            assertThat(hasConstraint(connection, "account_activation_expiry_check")).isTrue();

            assertDatabaseRejectsInvalidStates(connection);
            assertWrongQuestionsFollowStudentLifecycle(connection);
            assertUpdatedAtTriggers(connection);
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
