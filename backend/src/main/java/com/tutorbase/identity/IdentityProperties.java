package com.tutorbase.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tutor.identity")
public record IdentityProperties(
        Duration sessionLifetime,
        String csrfSecret,
        String cookieName,
        boolean cookieSecure,
        int publicMutationLimit,
        Duration publicMutationWindow,
        Bootstrap bootstrap) {
    public record Bootstrap(boolean enabled, String username, String password) {
    }
}
