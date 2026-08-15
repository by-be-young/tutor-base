package com.tutorbase.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/account-activations")
final class AdminAccountActivationController {
    private final IdentityService identity;

    AdminAccountActivationController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping
    ResponseEntity<ActivationCodeResponse> issue(
            @Valid @RequestBody IssueActivationRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AccountPrincipal administrator) {
        IdentityService.IssuedActivation issued = identity.issueActivation(
                request.accountId(), administrator.accountId());
        return ResponseEntity.created(URI.create("/api/v1/admin/account-activations/" + issued.activationId()))
                .cacheControl(CacheControl.noStore())
                .body(new ActivationCodeResponse(
                        issued.activationId(), issued.activationCode(), issued.expiresAt()));
    }

    record IssueActivationRequest(@Positive long accountId) {
    }

    record ActivationCodeResponse(long activationId, String activationCode, Instant expiresAt) {
    }
}
