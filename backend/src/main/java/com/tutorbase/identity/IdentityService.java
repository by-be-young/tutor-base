package com.tutorbase.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityService {
    static final String SESSION_ATTRIBUTE = IdentityService.class.getName() + ".session";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityStore store;
    private final PasswordEncoder passwords;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    IdentityService(IdentityStore store, PasswordEncoder passwords, IdentityProperties properties, Clock clock) {
        this.store = store;
        this.passwords = passwords;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwords.encode("not-a-real-account-password");
    }

    Optional<IdentityStore.SessionRow> findSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return store.session(hash(token), clock.instant());
    }

    String newAnonymousSession() {
        return newToken();
    }

    @Transactional
    LoginResult login(String username, String password, String previousToken) {
        Optional<IdentityStore.AccountRow> found = store.accountByUsername(normalize(username));
        String hash = found.filter(row -> "active".equals(row.status()))
                .map(IdentityStore.AccountRow::passwordHash).orElse(dummyPasswordHash);
        if (!passwords.matches(password, hash) || found.isEmpty() || !"active".equals(found.get().status())) {
            throw new InvalidCredentials();
        }
        IdentityStore.AccountRow account = found.get();
        revoke(previousToken);
        return new LoginResult(createSession(account.id()), principal(account));
    }

    @Transactional
    void activate(String username, String activationCode, String password) {
        String normalized = normalize(username);
        String encoded = passwords.encode(password);
        Instant now = clock.instant();
        int changed = store.activateWithStoredToken(normalized, hash(activationCode), encoded, now);
        if (changed != 1) {
            throw new InvalidActivation();
        }
    }

    @Transactional
    IssuedActivation issueActivation(long accountId, long createdBy) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        String status = store.lockAccountStatus(accountId).orElseThrow(AccountNotFound::new);
        if (!"pending_activation".equals(status)) {
            throw new AccountStateConflict();
        }
        Instant expiresAt = now.plusSeconds(86_400);
        long activationId = store.replaceActivation(accountId, createdBy, hash(token), expiresAt, now);
        return new IssuedActivation(activationId, token, expiresAt);
    }

    @Transactional
    void bootstrapAdministrator(String username, String password) {
        if (!properties.bootstrap().enabled()) {
            throw new InvalidActivation();
        }
        String normalizedUsername = normalize(username);
        Instant now = clock.instant();
        store.prepareBootstrapAdministrator(normalizedUsername);
        if (store.activateBootstrap(normalizedUsername, passwords.encode(password), now) != 1) {
            throw new InvalidActivation();
        }
    }

    @Transactional
    void setLearnerPassword(long learnerId, String password) {
        String passwordHash = passwords.encode(password);
        switch (store.setLearnerPassword(learnerId, passwordHash, clock.instant())) {
            case CHANGED -> {
            }
            case NOT_FOUND -> throw new AccountNotFound();
            case STATE_CONFLICT -> throw new AccountStateConflict();
        }
    }

    void revoke(String token) {
        if (token != null && !token.isBlank()) {
            store.revoke(hash(token), clock.instant());
        }
    }

    String csrfToken(String sessionToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.csrfSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(sessionToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate CSRF token", exception);
        }
    }

    boolean validCsrf(String sessionToken, String csrfToken) {
        return sessionToken != null && !sessionToken.isBlank() && csrfToken != null && MessageDigest.isEqual(
                csrfToken(sessionToken).getBytes(StandardCharsets.US_ASCII),
                csrfToken.getBytes(StandardCharsets.US_ASCII));
    }

    private String createSession(long accountId) {
        String token = newToken();
        Instant now = clock.instant();
        store.createSession(hash(token), accountId, now.plus(properties.sessionLifetime()), now);
        return token;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AccountPrincipal principal(IdentityStore.AccountRow account) {
        return new AccountPrincipal(account.id(), account.learnerId(), account.username(), account.role());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record LoginResult(String token, AccountPrincipal principal) {
    }

    record IssuedActivation(long activationId, String activationCode, Instant expiresAt) {
    }

    static final class InvalidCredentials extends RuntimeException {
    }

    static final class InvalidActivation extends RuntimeException {
    }

    static final class AccountNotFound extends RuntimeException {
    }

    static final class AccountStateConflict extends RuntimeException {
    }
}
