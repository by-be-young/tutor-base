package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.tutorbase.shared.http.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        "tutor.identity.csrf-secret=reward-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class RewardHttpContractTest {

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
        jdbc.update("DELETE FROM public.card_collection");
        jdbc.update("DELETE FROM public.task_claims");
        jdbc.update("DELETE FROM public.daily_check_in");
        jdbc.update("DELETE FROM public.user_points");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{}')");
        insertActiveAccount(5, "Alice", "learner", "Learner-password-2026");
    }

    @Test
    void learnerReadsOnlyOwnBalanceAndCollection() throws Exception {
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (6, 'Bob', '{}')");
        jdbc.update("INSERT INTO public.user_points (student_id, points) VALUES (5, 400), (6, 900)");
        jdbc.update("""
                INSERT INTO public.card_collection
                    (student_id, milestone_points, card_key, set_key, rarity)
                VALUES (5, 200, 'flame-c0', 'flame', 'common'),
                       (6, 200, 'private-card', 'forest', 'common')
                """);
        Cookie learner = login("Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/rewards").cookie(learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(400))
                .andExpect(jsonPath("$.collection.length()").value(1))
                .andExpect(jsonPath("$.collection[0].cardKey").value("flame-c0"));
    }

    @Test
    void reachedMilestoneCreatesServerSelectedCard() throws Exception {
        jdbc.update("INSERT INTO public.user_points (student_id, points) VALUES (5, 1000)");
        Cookie learner = login("Alice", "Learner-password-2026");
        BrowserSession browser = csrf(learner);

        mockMvc.perform(post("/api/v1/rewards/milestones/1000/claims")
                        .cookie(learner)
                        .header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestonePoints").value(1000))
                .andExpect(jsonPath("$.cardKey").value("flame-r"))
                .andExpect(jsonPath("$.setKey").value("flame"))
                .andExpect(jsonPath("$.rarity").value("rare"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class)).isEqualTo(1);
    }

    @Test
    void unreachedDuplicateAndInvalidMilestonesAreRejectedWithoutExtraRows() throws Exception {
        jdbc.update("INSERT INTO public.user_points (student_id, points) VALUES (5, 400)");
        Cookie learner = login("Alice", "Learner-password-2026");
        BrowserSession browser = csrf(learner);

        mockMvc.perform(post("/api/v1/rewards/milestones/600/claims")
                        .cookie(learner).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("milestone_not_reached"));

        mockMvc.perform(post("/api/v1/rewards/milestones/200/claims")
                        .cookie(learner).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rewards/milestones/200/claims")
                        .cookie(learner).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("reward_already_claimed"));

        mockMvc.perform(post("/api/v1/rewards/milestones/201/claims")
                        .cookie(learner).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class)).isEqualTo(1);
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mockMvc.perform(get("/api/v1/rewards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mockMvc.perform(post("/api/v1/rewards/milestones/200/claims"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    private Cookie login(String username, String password) throws Exception {
        BrowserSession browser = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(result);
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        Cookie cookie = existingCookie == null ? requireCookie(result) : existingCookie;
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
        return new BrowserSession(cookie, token);
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
