package com.tutorbase.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class TeacherApplicationStore {
    private final JdbcClient jdbc;

    TeacherApplicationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean accountExists(String usernameNormalized) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public.account WHERE username_normalized = :username
                )
                """).param("username", usernameNormalized).query(Boolean.class).single();
    }

    boolean pendingApplicationExists(String usernameNormalized) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public.teacher_registration_application
                    WHERE username_normalized = :username AND status = 'pending'
                )
                """).param("username", usernameNormalized).query(Boolean.class).single();
    }

    long insertPending(String username, String usernameNormalized, String passwordHash, Instant now) {
        return jdbc.sql("""
                INSERT INTO public.teacher_registration_application
                    (username, username_normalized, password_hash, status, created_at)
                VALUES (:username, :normalized, :passwordHash, 'pending', :now)
                RETURNING id
                """).param("username", username)
                .param("normalized", usernameNormalized)
                .param("passwordHash", passwordHash)
                .param("now", dbTime(now))
                .query(Long.class)
                .single();
    }

    Optional<ApplicationRow> lockById(long applicationId) {
        return jdbc.sql("""
                SELECT id, username, username_normalized, password_hash, status,
                       created_at, reviewed_at, rejection_reason, approved_account_id
                FROM public.teacher_registration_application
                WHERE id = :id
                FOR UPDATE
                """).param("id", applicationId).query(TeacherApplicationStore::application).optional();
    }

    long createAdministratorAccount(
            String username,
            String usernameNormalized,
            String passwordHash,
            Instant now) {
        return jdbc.sql("""
                INSERT INTO public.account
                    (username, username_normalized, password_hash, status, role, activated_at)
                VALUES (:username, :normalized, :passwordHash, 'active', 'administrator', :now)
                RETURNING id
                """).param("username", username)
                .param("normalized", usernameNormalized)
                .param("passwordHash", passwordHash)
                .param("now", dbTime(now))
                .query(Long.class)
                .single();
    }

    int markApproved(long applicationId, long reviewedBy, long approvedAccountId, Instant now) {
        return jdbc.sql("""
                UPDATE public.teacher_registration_application
                SET status = 'approved',
                    reviewed_by = :reviewedBy,
                    reviewed_at = :now,
                    approved_account_id = :approvedAccountId
                WHERE id = :id AND status = 'pending'
                """).param("id", applicationId)
                .param("reviewedBy", reviewedBy)
                .param("approvedAccountId", approvedAccountId)
                .param("now", dbTime(now))
                .update();
    }

    int markRejected(long applicationId, long reviewedBy, String reason, Instant now) {
        return jdbc.sql("""
                UPDATE public.teacher_registration_application
                SET status = 'rejected',
                    reviewed_by = :reviewedBy,
                    reviewed_at = :now,
                    rejection_reason = :reason
                WHERE id = :id AND status = 'pending'
                """).param("id", applicationId)
                .param("reviewedBy", reviewedBy)
                .param("reason", reason)
                .param("now", dbTime(now))
                .update();
    }

    List<ApplicationRow> list(String status) {
        if (status == null) {
            return jdbc.sql("""
                    SELECT id, username, username_normalized, password_hash, status,
                           created_at, reviewed_at, rejection_reason, approved_account_id
                    FROM public.teacher_registration_application
                    ORDER BY created_at DESC, id DESC
                    LIMIT 500
                    """).query(TeacherApplicationStore::application).list();
        }
        return jdbc.sql("""
                SELECT id, username, username_normalized, password_hash, status,
                       created_at, reviewed_at, rejection_reason, approved_account_id
                FROM public.teacher_registration_application
                WHERE status = :status
                ORDER BY created_at DESC, id DESC
                LIMIT 500
                """).param("status", status).query(TeacherApplicationStore::application).list();
    }

    long pendingCount() {
        return jdbc.sql("""
                SELECT count(*) FROM public.teacher_registration_application WHERE status = 'pending'
                """).query(Long.class).single();
    }

    private static ApplicationRow application(ResultSet rs, int rowNumber) throws SQLException {
        return new ApplicationRow(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("username_normalized"),
                rs.getString("password_hash"),
                rs.getString("status"),
                instant(rs, "created_at"),
                instant(rs, "reviewed_at"),
                rs.getString("rejection_reason"),
                rs.getObject("approved_account_id", Long.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime dbTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    record ApplicationRow(
            long id,
            String username,
            String usernameNormalized,
            String passwordHash,
            String status,
            Instant createdAt,
            Instant reviewedAt,
            String rejectionReason,
            Long approvedAccountId) {
    }
}
