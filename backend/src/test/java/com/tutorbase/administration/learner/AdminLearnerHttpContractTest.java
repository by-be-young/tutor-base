package com.tutorbase.administration.learner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbase.shared.http.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties =
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
class AdminLearnerHttpContractTest {

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
    private WebApplicationContext applicationContext;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvcAndData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                2L, "alice", "{9,3}");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                5L, "bob", "{}");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                9L, "carol", "{11}");
    }

    @Test
    void givenAdministratorWhenListingLearnersThenMapsRowsAndUsesOpaqueCursorPagination() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/v1/admin/learners")
                        .param("limit", "2")
                        .with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].learnerId").value(2))
                .andExpect(jsonPath("$.items[0].username").value("alice"))
                .andExpect(jsonPath("$.items[0].contentGrantArticleIds[0]").value(3))
                .andExpect(jsonPath("$.items[0].contentGrantArticleIds[1]").value(9))
                .andExpect(jsonPath("$.items[1].learnerId").value(5))
                .andExpect(jsonPath("$.items[1].contentGrantArticleIds").isArray())
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();

        String nextCursor = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor")
                .asText();

        mockMvc.perform(get("/api/v1/admin/learners")
                        .param("cursor", nextCursor)
                        .param("limit", "2")
                        .with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].learnerId").value(9))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void givenNoAuthenticationWhenListingLearnersThenReturnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/learners"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void givenNonAdministratorWhenListingLearnersThenReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/learners").with(user("learner").roles("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void givenInvalidLimitsWhenListingLearnersThenReturnsValidationProblem() throws Exception {
        for (String invalidLimit : new String[] {"0", "101", "not-a-number"}) {
            mockMvc.perform(get("/api/v1/admin/learners")
                            .param("limit", invalidLimit)
                            .with(user("admin").roles("ADMINISTRATOR")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("validation_failed"));
        }
    }

    @Test
    void givenMalformedOrUnknownCursorWhenListingLearnersThenReturnsMalformedRequest() throws Exception {
        for (String invalidCursor : new String[] {"not-base64!", "djI6NQ"}) {
            mockMvc.perform(get("/api/v1/admin/learners")
                            .param("cursor", invalidCursor)
                            .with(user("admin").roles("ADMINISTRATOR")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("malformed_request"));
        }
    }
}
