package com.tutorbase;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.tutorbase.shared.http.TraceIdFilter;
import com.tutorbase.administration.learner.LearnerAdministration;
import com.tutorbase.administration.learner.LearnerQuery;
import com.tutorbase.learning.access.ContentGrantQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "tutor.web.allowed-origins=https://learn.be-young.top",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "management.health.db.enabled=false"
})
@Import(SystemHttpContractTest.FixedClockConfiguration.class)
class SystemHttpContractTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-14T08:30:00Z");

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @MockitoBean
    private LearnerQuery learnerQuery;

    @MockitoBean
    private LearnerAdministration learnerAdministration;

    @MockitoBean
    private ContentGrantQuery contentGrantQuery;

    @MockitoBean
    private JdbcClient jdbcClient;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void givenPublicStatusWhenRequestedThenReturnsStableContractWithFixedTime() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("tutor-base-backend"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.time").value("2026-08-14T08:30:00Z"));
    }

    @Test
    void givenPublicHealthProbesWhenRequestedThenBothAreAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void givenConfiguredFrontendOriginWhenPreflightRequestedThenAllowsCredentialedCors() throws Exception {
        mockMvc.perform(options("/api/v1/system/status")
                        .header(HttpHeaders.ORIGIN, "https://learn.be-young.top")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://learn.be-young.top"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void givenUnknownFrontendOriginWhenPreflightRequestedThenRejectsCors() throws Exception {
        mockMvc.perform(options("/api/v1/system/status")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void givenNoSessionWhenUnknownApiPathRequestedThenReturnsUnauthenticatedProblem() throws Exception {
        mockMvc.perform(get("/api/v1/not-implemented"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.type").value("https://api.be-young.top/problems/unauthenticated"))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid session is required."))
                .andExpect(jsonPath("$.instance").value("/api/v1/not-implemented"))
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        }
    }
}
