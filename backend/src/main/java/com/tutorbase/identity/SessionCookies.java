package com.tutorbase.identity;

import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

final class SessionCookies {

    private SessionCookies() {
    }

    static void session(
            HttpServletResponse response,
            SessionAuthenticationFilter sessions,
            IdentityProperties properties,
            String token) {
        response.addHeader("Set-Cookie", ResponseCookie.from(sessions.cookieName(), token)
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Lax").path("/")
                .maxAge(properties.sessionLifetime()).build().toString());
    }

    static void clear(
            HttpServletResponse response,
            SessionAuthenticationFilter sessions,
            IdentityProperties properties) {
        response.addHeader("Set-Cookie", ResponseCookie.from(sessions.cookieName(), "")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ZERO).build().toString());
    }
}
