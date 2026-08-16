package com.tutorbase.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

@Component
final class IdentityRateLimiter {
    private final IdentityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    IdentityRateLimiter(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void check(String operation, String remoteAddress) {
        int limit = properties.publicMutationLimit();
        Duration duration = properties.publicMutationWindow();
        if (limit < 1 || duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("Public identity rate limit configuration is invalid");
        }
        Instant now = clock.instant();
        String key = operation + ':' + (remoteAddress == null ? "unknown" : remoteAddress);
        AtomicReference<RateLimited> blocked = new AtomicReference<>();
        windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.resetsAt())) {
                return new Window(1, now.plus(duration));
            }
            if (current.attempts() >= limit) {
                blocked.set(new RateLimited(Duration.between(now, current.resetsAt())));
                return current;
            }
            return new Window(current.attempts() + 1, current.resetsAt());
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetsAt()));
        }
        if (blocked.get() != null) {
            throw blocked.get();
        }
    }

    private record Window(int attempts, Instant resetsAt) {
    }

    static final class RateLimited extends RuntimeException {
        private final Duration retryAfter;

        RateLimited(Duration retryAfter) {
            this.retryAfter = retryAfter;
        }

        Duration retryAfter() {
            return retryAfter;
        }
    }
}
