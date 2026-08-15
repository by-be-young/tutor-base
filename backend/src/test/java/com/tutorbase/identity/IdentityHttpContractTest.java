package com.tutorbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=identity-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class IdentityHttpContractTest {

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
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}')");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{9,3}')");
        insertAccount(2, "young", "administrator", "active", "Admin-password-2026");
        insertAccount(5, "Alice", "learner", "pending_activation", null);
    }

    @Test
    void givenTrustedBrowserWhenAuthenticatingThenSessionRotatesAuthorizesAndLogsOut() throws Exception {
        BrowserSession anonymous = csrf(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.account_session", Long.class)).isZero();

        mockMvc.perform(post("/api/v1/sessions")
                        .cookie(anonymous.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"young\",\"password\":\"Admin-password-2026\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));

        MvcResult login = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(anonymous.cookie())
                        .header("X-CSRF-TOKEN", anonymous.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"young\",\"password\":\"Admin-password-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accountId").isNumber())
                .andExpect(jsonPath("$.learnerId").value(2))
                .andExpect(jsonPath("$.username").value("young"))
                .andExpect(jsonPath("$.roles[0]").value("ADMINISTRATOR"))
                .andReturn();

        Cookie authenticatedCookie = requireCookie(login);
        assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymous.cookie().getValue());

        mockMvc.perform(get("/api/v1/session").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.roles[0]").value("ADMINISTRATOR"));

        mockMvc.perform(get("/api/v1/admin/learners").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        BrowserSession authenticated = csrf(authenticatedCookie);
        mockMvc.perform(delete("/api/v1/session")
                        .cookie(authenticatedCookie)
                        .header("X-CSRF-TOKEN", authenticated.csrfToken()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(get("/api/v1/session").cookie(authenticatedCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void givenPendingLearnerWhenAdministratorIssuesCodeThenActivationIsSingleUse() throws Exception {
        BrowserSession anonymous = csrf(null);
        Cookie adminCookie = login(anonymous, "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        long learnerAccountId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username_normalized = 'alice'", Long.class);

        MvcResult issued = mockMvc.perform(post("/api/v1/admin/account-activations")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + learnerAccountId + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/v1/admin/account-activations/")))
                .andExpect(jsonPath("$.activationCode").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        String activationCode = json(issued).get("activationCode").asText();
        assertThat(jdbc.queryForObject(
                "SELECT encode(token_hash, 'hex') FROM public.account_activation", String.class))
                .doesNotContain(activationCode);

        mockMvc.perform(post("/api/v1/account-activations/complete")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "Alice",
                                "activationCode", activationCode,
                                "password", "Learner-password-2026"))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM public.account_activation", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM public.account WHERE id = ?", String.class, learnerAccountId)).isEqualTo("active");

        mockMvc.perform(post("/api/v1/account-activations/complete")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "Alice",
                                "activationCode", activationCode,
                                "password", "Another-password-2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_activation_token"));
    }

    @Test
    void givenUnknownOrPendingAccountWhenLoggingInThenErrorDoesNotRevealAccountState() throws Exception {
        BrowserSession anonymous = csrf(null);
        String pendingBody = invalidLogin(anonymous, "Alice", "wrong-password");
        String unknownBody = invalidLogin(anonymous, "nobody", "wrong-password");
        assertThat(jsonTextWithoutTraceId(pendingBody)).isEqualTo(jsonTextWithoutTraceId(unknownBody));
    }

    @Test
    void givenMissingOrLearnerSessionWhenSettingPasswordThenAccessIsDenied() throws Exception {
        BrowserSession anonymous = csrf(null);
        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(anonymous.cookie())
                        .header("X-CSRF-TOKEN", anonymous.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"New-password-2026\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        setActivePassword(5, "Learner-password-2026");
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");
        BrowserSession learner = csrf(learnerCookie);
        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(learnerCookie)
                        .header("X-CSRF-TOKEN", learner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"New-password-2026\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void givenAdministratorWhenPasswordIsWeakOrLearnerIsUnknownThenReturnsProblem() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Valid-password-2026\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));

        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"too-short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(put("/api/v1/admin/learners/999/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Valid-password-2026\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));

        mockMvc.perform(put("/api/v1/admin/learners/2/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Valid-password-2026\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("state_conflict"));

        jdbc.update("UPDATE public.account SET status = 'disabled' WHERE learner_id = 5");
        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Valid-password-2026\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("state_conflict"));
    }

    @Test
    void givenPendingLearnerWhenAdministratorSetsPasswordThenLearnerCanLoginAndActivationIsRevoked()
            throws Exception {
        long learnerAccountId = accountId("alice");
        jdbc.update("""
                INSERT INTO public.account_activation(account_id, token_hash, expires_at, created_by)
                VALUES (?, decode(repeat('ab', 32), 'hex'), now() + interval '1 day', ?)
                """, learnerAccountId, accountId("young"));
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Learner-password-2026\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM public.account WHERE learner_id = 5", String.class)).isEqualTo("active");
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM public.account_activation WHERE account_id = ?",
                Boolean.class, learnerAccountId)).isTrue();
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");
        mockMvc.perform(get("/api/v1/session").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerId").value(5));
    }

    @Test
    void givenActiveLearnerWhenAdministratorResetsPasswordThenOldCredentialAndSessionsAreInvalid()
            throws Exception {
        setActivePassword(5, "Old-password-2026");
        Object originalActivatedAt = jdbc.queryForObject(
                "SELECT activated_at FROM public.account WHERE learner_id = 5", Object.class);
        Cookie learnerCookie = login(csrf(null), "Alice", "Old-password-2026");
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(put("/api/v1/admin/learners/5/password")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"New-password-2026\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT activated_at FROM public.account WHERE learner_id = 5", Object.class))
                .isEqualTo(originalActivatedAt);

        mockMvc.perform(get("/api/v1/session").cookie(learnerCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mockMvc.perform(get("/api/v1/session").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMINISTRATOR"));
        invalidLogin(csrf(null), "Alice", "Old-password-2026");
        Cookie renewed = login(csrf(null), "Alice", "New-password-2026");
        mockMvc.perform(get("/api/v1/session").cookie(renewed))
                .andExpect(status().isOk());
    }

    private String invalidLogin(BrowserSession browser, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andReturn().getResponse().getContentAsString();
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        Cookie cookie = existingCookie == null ? requireCookie(result) : existingCookie;
        return new BrowserSession(cookie, json(result).get("token").asText());
    }

    private Cookie login(BrowserSession browser, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(result);
    }

    private Cookie requireCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("TUTOR_SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String jsonTextWithoutTraceId(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        ((tools.jackson.databind.node.ObjectNode) node).remove("traceId");
        return node.toString();
    }

    private void insertAccount(
            long learnerId,
            String username,
            String role,
            String status,
            String password) {
        String passwordHash = password == null ? null : passwords.encode(password);
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, lower(?), ?, ?, ?, CASE WHEN ? = 'active' THEN now() ELSE NULL END)
                """, learnerId, username, username, passwordHash, status, role, status);
    }

    private void setActivePassword(long learnerId, String password) {
        jdbc.update("""
                UPDATE public.account
                SET password_hash = ?, status = 'active', activated_at = now()
                WHERE learner_id = ?
                """, passwords.encode(password), learnerId);
    }

    private long accountId(String normalizedUsername) {
        return jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username_normalized = ?",
                Long.class,
                normalizedUsername);
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
