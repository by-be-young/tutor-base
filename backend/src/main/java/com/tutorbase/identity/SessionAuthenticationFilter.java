package com.tutorbase.identity;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final IdentityService identity;
    private final IdentityProperties properties;

    SessionAuthenticationFilter(IdentityService identity, IdentityProperties properties) {
        this.identity = identity;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookie(request);
        identity.findSession(token).ifPresent(session -> {
            request.setAttribute(IdentityService.SESSION_ATTRIBUTE, session);
            AccountPrincipal principal = session.principal();
            if (principal != null) {
                String authority = "ROLE_" + principal.role().toUpperCase(java.util.Locale.ROOT);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of(new SimpleGrantedAuthority(authority))));
            }
        });
        chain.doFilter(request, response);
    }

    String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    String cookieName() {
        return properties.cookieName();
    }
}
