package com.tutorbase.shared.security;

import com.tutorbase.identity.SessionAuthenticationFilter;
import com.tutorbase.identity.SessionCsrfFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("integration")
class MigrationOnlySecurityConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfiguration.class)
            .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
            .withBean(SessionAuthenticationFilter.class, () -> mock(SessionAuthenticationFilter.class))
            .withBean(SessionCsrfFilter.class, () -> mock(SessionCsrfFilter.class));

    @Test
    void givenNonWebMigrationContextWhenSecurityLoadsThenWebFilterChainIsSkipped() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).hasSingleBean(PasswordEncoder.class);
        });
    }
}
