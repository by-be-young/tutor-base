package com.tutorbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.bootstrap.enabled=true",
        "tutor.identity.csrf-secret=bootstrap-integration-test-secret"
})
class BootstrapAdministratorIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private IdentityService identity;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearIdentityData() {
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
    }

    @Test
    void createsAndActivatesFirstAdministratorWithoutLearnerRecord() {
        identity.bootstrapAdministrator("young", "Admin-password-2026");

        assertThat(jdbc.queryForMap("""
                SELECT username, username_normalized, learner_id, status, role,
                       password_hash IS NOT NULL AS password_configured,
                       activated_at IS NOT NULL AS activated
                FROM public.account
                """))
                .containsEntry("username", "young")
                .containsEntry("username_normalized", "young")
                .containsEntry("learner_id", null)
                .containsEntry("status", "active")
                .containsEntry("role", "administrator")
                .containsEntry("password_configured", true)
                .containsEntry("activated", true);
    }

    @Test
    void rollsBackWhenBootstrapUsernameBelongsToLearner() {
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (3, 'young', '{}')");
        jdbc.update("""
                INSERT INTO public.account (id, learner_id, username, username_normalized)
                VALUES (3, 3, 'young', 'young')
                """);

        assertThatThrownBy(() -> identity.bootstrapAdministrator("young", "Admin-password-2026"))
                .isInstanceOf(IdentityService.InvalidActivation.class);

        assertThat(jdbc.queryForObject(
                "SELECT role FROM public.account WHERE username_normalized = 'young'", String.class))
                .isEqualTo("learner");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.account WHERE role = 'administrator'", Long.class)).isZero();
    }

    @Test
    void refusesSecondAdministrator() {
        identity.bootstrapAdministrator("young", "Admin-password-2026");

        assertThatThrownBy(() -> identity.bootstrapAdministrator("another-admin", "Other-password-2026"))
                .isInstanceOf(IdentityService.InvalidActivation.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.account WHERE role = 'administrator'", Long.class)).isEqualTo(1L);
    }
}
