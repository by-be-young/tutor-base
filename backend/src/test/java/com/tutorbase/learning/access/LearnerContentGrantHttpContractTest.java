package com.tutorbase.learning.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbase.shared.http.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=learner-content-grant-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class LearnerContentGrantHttpContractTest {

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

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvcAndData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(traceIdFilter)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}')");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{9,3}')");
        insertActiveAccount(2, "young", "administrator", "Admin-password-2026");
        insertActiveAccount(5, "Alice", "learner", "Learner-password-2026");
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.student', 'id'), 5, true)", Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.account', 'id'), "
                        + "(SELECT max(id) FROM public.account), true)",
                Long.class);
    }

    @Test
    void administratorCreatesLearnerAndReplacesContentGrantsAtomically() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/learners")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  Bob  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/admin/learners/[0-9]+")))
                .andExpect(jsonPath("$.username").value("Bob"))
                .andExpect(jsonPath("$.contentGrantArticleIds").isEmpty())
                .andReturn();

        long learnerId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("learnerId")
                .asLong();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.account WHERE learner_id = ? "
                        + "AND username_normalized = 'bob' AND status = 'pending_activation' AND role = 'learner'",
                Long.class,
                learnerId)).isEqualTo(1);

        mockMvc.perform(put("/api/v1/admin/learners/{learnerId}/content-grants", learnerId)
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[9,3,9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerId").value(learnerId))
                .andExpect(jsonPath("$.contentGrantArticleIds[0]").value(3))
                .andExpect(jsonPath("$.contentGrantArticleIds[1]").value(9));
    }

    @Test
    void learnerReadsOnlyContentGrantsLinkedToAuthenticatedSession() throws Exception {
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/me/content-grants").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleIds[0]").value(3))
                .andExpect(jsonPath("$.articleIds[1]").value(9));

        mockMvc.perform(get("/api/v1/me/content-grants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void learnerCannotAdministerLearnersAndNormalizedUsernameConflictsRollback() throws Exception {
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");
        BrowserSession learner = csrf(learnerCookie);
        mockMvc.perform(post("/api/v1/admin/learners")
                        .cookie(learnerCookie)
                        .header("X-CSRF-TOKEN", learner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"forbidden\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(post("/api/v1/admin/learners")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("username_conflict"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.student WHERE username = 'alice'", Long.class)).isZero();
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = existingCookie == null ? requireCookie(result) : existingCookie;
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        return new BrowserSession(cookie, token);
    }

    private Cookie login(BrowserSession browser, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(result);
    }

    private Cookie requireCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("TUTOR_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void insertActiveAccount(long learnerId, String username, String role, String password) {
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, lower(?), ?, 'active', ?, now())
                """, learnerId, username, username, passwords.encode(password), role);
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
