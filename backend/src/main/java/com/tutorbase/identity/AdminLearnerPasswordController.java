package com.tutorbase.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/admin/learners")
class AdminLearnerPasswordController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminLearnerPasswordController.class);

    private final IdentityService identity;

    AdminLearnerPasswordController(IdentityService identity) {
        this.identity = identity;
    }

    @PutMapping("/{learnerId}/password")
    ResponseEntity<Void> setPassword(
            @PathVariable @Positive long learnerId,
            @Valid @RequestBody SetLearnerPasswordRequest request,
            @AuthenticationPrincipal AccountPrincipal administrator) {
        identity.setLearnerPassword(learnerId, request.password());
        LOGGER.info("Administrator account {} set password for learner {}", administrator.accountId(), learnerId);
        return ResponseEntity.noContent().build();
    }

    record SetLearnerPasswordRequest(@NotBlank @Size(min = 12, max = 128) String password) {
    }
}
