package com.tutorbase.identity;

import java.io.IOException;
import java.util.Set;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SessionCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final IdentityService identity;
    private final SessionAuthenticationFilter sessions;
    private final ObjectMapper objectMapper;

    SessionCsrfFilter(
            IdentityService identity,
            SessionAuthenticationFilter sessions,
            ObjectMapper objectMapper) {
        this.identity = identity;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String sessionToken = sessions.cookie(request);
        if (identity.validCsrf(sessionToken, request.getHeader("X-CSRF-TOKEN"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiProblem.create(
                HttpStatus.FORBIDDEN, "csrf_invalid", "Invalid CSRF token",
                "Fetch a fresh CSRF token before retrying the request.", request));
    }
}
