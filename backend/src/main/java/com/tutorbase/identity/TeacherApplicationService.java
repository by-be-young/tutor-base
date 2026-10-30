package com.tutorbase.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TeacherApplicationService {
    private final TeacherApplicationStore store;
    private final PasswordEncoder passwords;
    private final Clock clock;

    TeacherApplicationService(TeacherApplicationStore store, PasswordEncoder passwords, Clock clock) {
        this.store = store;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    Application submit(String username, String password) {
        String trimmed = username.strip();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (store.accountExists(normalized) || store.pendingApplicationExists(normalized)) {
            throw new UsernameConflict();
        }
        Instant now = clock.instant();
        try {
            long id = store.insertPending(trimmed, normalized, passwords.encode(password), now);
            return new Application(id, trimmed, "pending", now, null, null, null);
        } catch (DataIntegrityViolationException exception) {
            // 并发提交同一用户名时由 pending 部分唯一索引兜底。
            throw new UsernameConflict();
        }
    }

    @Transactional(readOnly = true)
    List<Application> list(String status) {
        return store.list(status).stream().map(Application::from).toList();
    }

    @Transactional(readOnly = true)
    long pendingCount() {
        return store.pendingCount();
    }

    @Transactional
    ApprovedAccount approve(long applicationId, long reviewerAccountId) {
        TeacherApplicationStore.ApplicationRow application = store.lockById(applicationId)
                .orElseThrow(ApplicationNotFound::new);
        if (!"pending".equals(application.status())) {
            throw new ApplicationStateConflict();
        }
        Instant now = clock.instant();
        long accountId;
        try {
            accountId = store.createAdministratorAccount(
                    application.username(),
                    application.usernameNormalized(),
                    application.passwordHash(),
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw new UsernameConflict();
        }
        if (store.markApproved(applicationId, reviewerAccountId, accountId, now) != 1) {
            throw new ApplicationStateConflict();
        }
        return new ApprovedAccount(applicationId, accountId, application.username(), "administrator", now);
    }

    @Transactional
    Application reject(long applicationId, long reviewerAccountId, String reason) {
        TeacherApplicationStore.ApplicationRow application = store.lockById(applicationId)
                .orElseThrow(ApplicationNotFound::new);
        if (!"pending".equals(application.status())) {
            throw new ApplicationStateConflict();
        }
        String trimmedReason = reason.strip();
        if (trimmedReason.isEmpty()) {
            throw new InvalidRejectionReason();
        }
        Instant now = clock.instant();
        if (store.markRejected(applicationId, reviewerAccountId, trimmedReason, now) != 1) {
            throw new ApplicationStateConflict();
        }
        return new Application(applicationId, application.username(), "rejected",
                application.createdAt(), now, trimmedReason, null);
    }

    record Application(
            long id,
            String username,
            String status,
            Instant createdAt,
            Instant reviewedAt,
            String rejectionReason,
            Long approvedAccountId) {

        static Application from(TeacherApplicationStore.ApplicationRow row) {
            return new Application(
                    row.id(),
                    row.username(),
                    row.status().toUpperCase(Locale.ROOT),
                    row.createdAt(),
                    row.reviewedAt(),
                    row.rejectionReason(),
                    row.approvedAccountId());
        }
    }

    record ApprovedAccount(
            long applicationId,
            long accountId,
            String username,
            String role,
            Instant approvedAt) {
    }

    static final class UsernameConflict extends RuntimeException {
    }

    static final class ApplicationNotFound extends RuntimeException {
    }

    static final class ApplicationStateConflict extends RuntimeException {
    }

    static final class InvalidRejectionReason extends RuntimeException {
    }

    static final class SuperAdministratorRequired extends RuntimeException {
    }

    static final class InvalidApplicationStatus extends RuntimeException {
    }
}
