package com.tutorbase.identity;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/learners")
class AdminLearnerImpersonationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminLearnerImpersonationController.class);

    private final IdentityService identity;
    private final SessionAuthenticationFilter sessions;
    private final IdentityProperties properties;

    AdminLearnerImpersonationController(
            IdentityService identity,
            SessionAuthenticationFilter sessions,
            IdentityProperties properties) {
        this.identity = identity;
        this.sessions = sessions;
        this.properties = properties;
    }

    @PostMapping("/{learnerId}/impersonate")
    ResponseEntity<IdentityController.SessionResponse> impersonate(
            @PathVariable @Positive long learnerId,
            @AuthenticationPrincipal AccountPrincipal administrator,
            HttpServletResponse response) {
        IdentityService.LoginResult result = identity.impersonate(learnerId);
        SessionCookies.session(response, sessions, properties, result.token());
        LOGGER.info("Administrator account {} entered learner account {} ({})",
                administrator.accountId(), learnerId, result.principal().username());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IdentityController.SessionResponse.from(result.principal()));
    }
}
