package com.tutorbase.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class IdentityStore {
    private final JdbcClient jdbc;

    IdentityStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AccountRow> accountByUsername(String username) {
        return jdbc.sql("""
                SELECT id, learner_id, username, password_hash, status, role
                FROM public.account WHERE username_normalized = :username
                """).param("username", username).query(IdentityStore::account).optional();
    }

    Optional<SessionRow> session(byte[] tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT s.id, s.account_id, a.learner_id, a.username, a.role
                FROM public.account_session s
                LEFT JOIN public.account a ON a.id = s.account_id
                WHERE s.token_hash = :hash AND s.revoked_at IS NULL AND s.expires_at > :now
                  AND (s.account_id IS NULL OR a.status = 'active')
                """).param("hash", tokenHash).param("now", dbTime(now)).query(IdentityStore::session).optional();
    }

    void createSession(byte[] tokenHash, long accountId, Instant expiresAt, Instant now) {
        jdbc.sql("""
                INSERT INTO public.account_session(token_hash, account_id, expires_at, created_at, authenticated_at)
                VALUES (:hash, :accountId, :expiresAt, :now, :authenticatedAt)
                """).param("hash", tokenHash)
                .param("accountId", accountId)
                .param("expiresAt", dbTime(expiresAt))
                .param("now", dbTime(now))
                .param("authenticatedAt", dbTime(now), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    void deleteExpiredOrRevokedSessions(Instant now) {
        jdbc.sql("DELETE FROM public.account_session WHERE expires_at <= :now OR revoked_at IS NOT NULL")
                .param("now", dbTime(now)).update();
    }

    void revoke(byte[] tokenHash, Instant now) {
        jdbc.sql("UPDATE public.account_session SET revoked_at = :now WHERE token_hash = :hash AND revoked_at IS NULL")
                .param("now", dbTime(now)).param("hash", tokenHash).update();
    }

    int activateWithStoredToken(String username, byte[] tokenHash, String passwordHash, Instant now) {
        return jdbc.sql("""
                WITH consumed AS (
                    UPDATE public.account_activation t SET consumed_at = :now
                    FROM public.account a
                    WHERE a.id = t.account_id AND a.username_normalized = :username
                      AND a.status = 'pending_activation' AND t.token_hash = :tokenHash
                      AND t.consumed_at IS NULL AND t.revoked_at IS NULL AND t.expires_at > :now
                    RETURNING t.account_id
                )
                UPDATE public.account a SET password_hash = :passwordHash, status = 'active', activated_at = :now
                FROM consumed WHERE a.id = consumed.account_id
                """).param("passwordHash", passwordHash).param("now", dbTime(now))
                .param("username", username).param("tokenHash", tokenHash).update();
    }

    int activateBootstrap(String username, String passwordHash, Instant now) {
        return jdbc.sql("""
                UPDATE public.account SET password_hash = :passwordHash, status = 'active', activated_at = :now
                WHERE username_normalized = :username AND status = 'pending_activation' AND role = 'administrator'
                """).param("passwordHash", passwordHash).param("now", dbTime(now))
                .param("username", username).update();
    }

    void prepareBootstrapAdministrator(String username) {
        jdbc.sql("LOCK TABLE public.account IN SHARE ROW EXCLUSIVE MODE").update();
        jdbc.sql("""
                INSERT INTO public.account (username, username_normalized, role)
                SELECT :username, :username, 'administrator'
                WHERE NOT EXISTS (
                    SELECT 1 FROM public.account WHERE role = 'administrator'
                )
                ON CONFLICT (username_normalized) DO NOTHING
                """).param("username", username).update();
    }

    Optional<String> lockAccountStatus(long accountId) {
        return jdbc.sql("SELECT status FROM public.account WHERE id = :accountId FOR UPDATE")
                .param("accountId", accountId)
                .query(String.class)
                .optional();
    }

    long replaceActivation(long accountId, long createdBy, byte[] hash, Instant expiresAt, Instant now) {
        jdbc.sql("""
                UPDATE public.account_activation SET revoked_at = :now
                WHERE account_id = :accountId AND consumed_at IS NULL AND revoked_at IS NULL
                """).param("now", dbTime(now)).param("accountId", accountId).update();
        return jdbc.sql("""
                INSERT INTO public.account_activation(account_id, token_hash, expires_at, created_by, created_at)
                VALUES (:accountId, :hash, :expiresAt, :createdBy, :now)
                RETURNING id
                """).param("accountId", accountId).param("hash", hash).param("expiresAt", dbTime(expiresAt))
                .param("createdBy", createdBy).param("now", dbTime(now)).query(Long.class).single();
    }

    PasswordChangeResult setLearnerPassword(long learnerId, String passwordHash, Instant now) {
        String result = jdbc.sql("""
                WITH target AS (
                    SELECT id, role, status
                    FROM public.account WHERE learner_id = :learnerId FOR UPDATE
                ), eligible AS (
                    SELECT id FROM target
                    WHERE role = 'learner' AND status IN ('pending_activation', 'active')
                ), updated AS (
                    UPDATE public.account a
                    SET password_hash = :passwordHash,
                        status = 'active',
                        activated_at = COALESCE(a.activated_at, :now)
                    FROM eligible WHERE a.id = eligible.id
                    RETURNING a.id
                ), revoked_sessions AS (
                    UPDATE public.account_session s SET revoked_at = :now
                    FROM updated WHERE s.account_id = updated.id AND s.revoked_at IS NULL
                    RETURNING s.id
                ), revoked_activations AS (
                    UPDATE public.account_activation t SET revoked_at = :now
                    FROM updated
                    WHERE t.account_id = updated.id AND t.consumed_at IS NULL AND t.revoked_at IS NULL
                    RETURNING t.id
                )
                SELECT CASE
                    WHEN NOT EXISTS (SELECT 1 FROM target) THEN 'not_found'
                    WHEN NOT EXISTS (SELECT 1 FROM eligible) THEN 'state_conflict'
                    ELSE 'changed'
                END
                """).param("learnerId", learnerId)
                .param("passwordHash", passwordHash)
                .param("now", dbTime(now))
                .query(String.class)
                .single();
        return PasswordChangeResult.valueOf(result.toUpperCase(java.util.Locale.ROOT));
    }

    private static AccountRow account(ResultSet rs, int rowNumber) throws SQLException {
        return new AccountRow(rs.getLong("id"), rs.getObject("learner_id", Long.class), rs.getString("username"),
                rs.getString("password_hash"), rs.getString("status"), rs.getString("role"));
    }

    private static SessionRow session(ResultSet rs, int rowNumber) throws SQLException {
        Long accountId = rs.getObject("account_id", Long.class);
        if (accountId == null) {
            return new SessionRow(rs.getLong("id"), null);
        }
        return new SessionRow(rs.getLong("id"), new AccountPrincipal(accountId,
                rs.getObject("learner_id", Long.class),
                rs.getString("username"), rs.getString("role")));
    }

    private static OffsetDateTime dbTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    record AccountRow(long id, Long learnerId, String username, String passwordHash, String status, String role) {
    }

    record SessionRow(long id, AccountPrincipal principal) {
    }

    enum PasswordChangeResult {
        CHANGED,
        NOT_FOUND,
        STATE_CONFLICT
    }
}
