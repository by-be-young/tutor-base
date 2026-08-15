package com.tutorbase.shared.security;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.tutorbase.identity.IdentityProperties;
import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsSecureProductionConfiguration() {
        assertThatNoException().isThrownBy(() -> ProductionConfigurationValidator.validate(
                new WebClientProperties(List.of("https://learn.be-young.top")),
                identity("0123456789abcdef0123456789abcdef", true, false)));
    }

    @Test
    void rejectsMissingOrNonOriginCorsConfiguration() {
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                new WebClientProperties(List.of()), identity("0123456789abcdef0123456789abcdef", true, false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                new WebClientProperties(List.of("https://learn.be-young.top/path")),
                identity("0123456789abcdef0123456789abcdef", true, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWeakSecretInsecureCookieOrEnabledBootstrap() {
        WebClientProperties web = new WebClientProperties(List.of("https://learn.be-young.top"));
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                web, identity("too-short", true, false))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                web, identity("0123456789abcdef0123456789abcdef", false, false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                web, identity("0123456789abcdef0123456789abcdef", true, true)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static IdentityProperties identity(String csrfSecret, boolean secureCookie, boolean bootstrap) {
        return new IdentityProperties(
                Duration.ofHours(12),
                csrfSecret,
                "__Host-TUTOR_SESSION",
                secureCookie,
                10,
                Duration.ofMinutes(1),
                new IdentityProperties.Bootstrap(bootstrap, "young", ""));
    }
}
