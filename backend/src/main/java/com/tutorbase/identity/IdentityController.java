package com.tutorbase.identity;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
final class IdentityController {
    private final IdentityService identity;
    private final IdentityProperties properties;
    private final SessionAuthenticationFilter sessions;
    private final IdentityRateLimiter rateLimiter;

    IdentityController(
            IdentityService identity,
            IdentityProperties properties,
            SessionAuthenticationFilter sessions,
            IdentityRateLimiter rateLimiter) {
        this.identity = identity;
        this.properties = properties;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        String token = sessions.cookie(request);
        if (request.getAttribute(IdentityService.SESSION_ATTRIBUTE) == null) {
            token = identity.newAnonymousSession();
            setCookie(response, token);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(identity.csrfToken(token), "X-CSRF-TOKEN"));
    }

    @PostMapping("/sessions")
    ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        rateLimiter.check("login", request.getRemoteAddr());
        IdentityService.LoginResult result = identity.login(
                body.username(), body.password(), sessions.cookie(request));
        setCookie(response, result.token());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SessionResponse.from(result.principal()));
    }

    @GetMapping("/session")
    ResponseEntity<SessionResponse> current(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SessionResponse.from(principal));
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        identity.revoke(sessions.cookie(request));
        clearCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-activations/complete")
    ResponseEntity<Void> activate(@Valid @RequestBody ActivationRequest body, HttpServletRequest request) {
        rateLimiter.check("activation", request.getRemoteAddr());
        identity.activate(body.username(), body.activationCode(), body.password());
        return ResponseEntity.noContent().build();
    }

    private void setCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", ResponseCookie.from(sessions.cookieName(), token)
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Lax").path("/")
                .maxAge(properties.sessionLifetime()).build().toString());
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(sessions.cookieName(), "")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ZERO).build().toString());
    }

    record CsrfResponse(String token, String headerName) {
    }

    record LoginRequest(@NotBlank @Size(max = 100) String username,
                        @NotBlank @Size(max = 128) String password) {
    }

    record ActivationRequest(@NotBlank @Size(max = 100) String username,
                             @NotBlank @Size(max = 256) String activationCode,
                             @NotBlank @Size(min = 12, max = 128) String password) {
    }

    record SessionResponse(long accountId, Long learnerId, String username, List<String> roles) {
        static SessionResponse from(AccountPrincipal principal) {
            return new SessionResponse(principal.accountId(), principal.learnerId(),
                    principal.username(), List.of(principal.role().toUpperCase(java.util.Locale.ROOT)));
        }
    }
}
