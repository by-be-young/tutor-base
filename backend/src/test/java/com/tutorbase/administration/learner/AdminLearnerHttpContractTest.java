package com.tutorbase.administration.learner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.tutorbase.shared.http.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=admin-learner-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
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

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void configureMockMvcAndData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("UPDATE public.student SET claimed_by = NULL, claimed_at = NULL");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("DELETE FROM public.article_answer_keys");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                2L, "alice", "{9,3}");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                5L, "bob", "{}");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                9L, "carol", "{11}");
        insertActiveAdministrator(500L, "admin", "Admin-password-2026");
        insertActiveAdministrator(501L, "admin2", "Admin2-password-2026");
        insertActiveLearner(1001L, 2L, "alice", "Learner-password-2026");
        jdbc.update("""
                INSERT INTO public.account (id, learner_id, username, username_normalized, role)
                VALUES (?, ?, ?, lower(?), 'learner')
                """, 1002L, 5L, "bob", "bob");
        jdbc.update("""
                INSERT INTO public.account (id, learner_id, username, username_normalized, role)
                VALUES (?, ?, ?, lower(?), 'learner')
                """, 1003L, 9L, "carol", "carol");
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.student', 'id'), "
                        + "(SELECT max(id) FROM public.student), true)",
                Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.account', 'id'), "
                        + "(SELECT max(id) FROM public.account), true)",
                Long.class);
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

    @Test
    void givenUnclaimedLearnerWhenClaimingThenReturnsClaimerAndReleaseIsIdempotent() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(put("/api/v1/admin/learners/2/claim")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.learnerId").value(2))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.claimedByAccountId").value(500))
                .andExpect(jsonPath("$.claimedByUsername").value("admin"))
                .andExpect(jsonPath("$.claimedAt").isString())
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/v1/admin/learners/2/claim")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedByAccountId").value(500));

        mockMvc.perform(delete("/api/v1/admin/learners/2/claim")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/learners/2/claim")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/learners").param("limit", "10").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].learnerId").value(2))
                .andExpect(jsonPath("$.items[0].claimedByAccountId")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void givenLearnerClaimedByAnotherAdministratorWhenMutatingThenReturnsConflict() throws Exception {
        jdbc.update("UPDATE public.student SET claimed_by = 501, claimed_at = now() WHERE id = 5");
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        for (var request : List.of(
                put("/api/v1/admin/learners/5/claim")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()),
                delete("/api/v1/admin/learners/5/claim")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()),
                delete("/api/v1/admin/learners/5")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()))) {
            mockMvc.perform(request)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("learner_already_claimed"));
        }

        Cookie ownerCookie = login("admin2", "Admin2-password-2026");
        BrowserSession owner = csrf(ownerCookie);
        mockMvc.perform(delete("/api/v1/admin/learners/5/claim")
                        .cookie(ownerCookie).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/admin/learners/5/claim")
                        .cookie(ownerCookie).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedByAccountId").value(501))
                .andExpect(jsonPath("$.claimedByUsername").value("admin2"));
    }

    @Test
    void givenLearnerWhenDeletingThenRemovesAccountAndLearnerRow() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        claimLearner(5, adminCookie, admin);

        mockMvc.perform(delete("/api/v1/admin/learners/5")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.account WHERE learner_id = 5", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.student WHERE id = 5", Long.class)).isZero();
        mockMvc.perform(get("/api/v1/admin/learners").param("limit", "10").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].learnerId").value(2))
                .andExpect(jsonPath("$.items[1].learnerId").value(9));
    }

    @Test
    void givenAdministratorLearnerWhenDeletingThenReturnsProtectedAndListExcludesIt() throws Exception {
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                7L, "rootadmin", "{}");
        jdbc.update("""
                INSERT INTO public.account
                    (id, learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, ?, lower(?), ?, 'active', 'administrator', now())
                """, 1004L, 7L, "rootadmin", "rootadmin",
                passwords.encode("Root-admin-password-2026"));
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        claimLearner(7, adminCookie, admin);

        mockMvc.perform(delete("/api/v1/admin/learners/7")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("administrator_learner_protected"));

        mockMvc.perform(get("/api/v1/admin/learners").param("limit", "10").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].learnerId").value(9));
    }

    @Test
    void givenMissingLearnerWhenClaimingOrDeletingThenReturnsNotFound() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        for (var request : List.of(
                put("/api/v1/admin/learners/9999/claim")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()),
                delete("/api/v1/admin/learners/9999/claim")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()),
                delete("/api/v1/admin/learners/9999")
                        .cookie(adminCookie).header("X-CSRF-TOKEN", admin.csrfToken()))) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("resource_not_found"));
        }
    }

    @Test
    void givenNoAuthenticationWhenMutatingLearnersThenReturnsUnauthenticated() throws Exception {
        BrowserSession anonymous = csrf(null);
        mockMvc.perform(put("/api/v1/admin/learners/2/claim")
                        .cookie(anonymous.cookie())
                        .header("X-CSRF-TOKEN", anonymous.csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void givenNonAdministratorWhenMutatingLearnersThenReturnsForbidden() throws Exception {
        Cookie learnerCookie = login("alice", "Learner-password-2026");
        BrowserSession learner = csrf(learnerCookie);
        mockMvc.perform(put("/api/v1/admin/learners/5/claim")
                        .cookie(learnerCookie)
                        .header("X-CSRF-TOKEN", learner.csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void givenInvalidLearnerIdWhenClaimingThenReturnsValidationProblem() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put("/api/v1/admin/learners/0/claim")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void givenAdministratorWhenCreatingLearnerThenNewLearnerIsClaimedByCreator() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        MvcResult created = mockMvc.perform(post("/api/v1/admin/learners")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "  Dana  "))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/admin/learners/[0-9]+")))
                .andExpect(jsonPath("$.username").value("Dana"))
                .andExpect(jsonPath("$.contentGrantArticleIds").isEmpty())
                .andExpect(jsonPath("$.enrolledSubjects").isEmpty())
                .andExpect(jsonPath("$.isTest").value(true))
                .andExpect(jsonPath("$.isSpecialTest").value(false))
                .andExpect(jsonPath("$.claimedByAccountId").value(500))
                .andExpect(jsonPath("$.claimedByUsername").value("admin"))
                .andExpect(jsonPath("$.claimedAt").isString())
                .andReturn();

        long learnerId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("learnerId")
                .asLong();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.account WHERE learner_id = ? AND role = 'learner'",
                Long.class,
                learnerId)).isEqualTo(1);
    }

    @Test
    void givenLearnerActivityWhenListingThenReportsLastActivityTimestamp() throws Exception {
        jdbc.update("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text, updated_at)
                VALUES (3, 2, 'q1', 'draft', '2026-10-01T00:00:00Z')
                """);
        mockMvc.perform(get("/api/v1/admin/learners")
                        .param("limit", "1")
                        .with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].learnerId").value(2))
                .andExpect(jsonPath("$.items[0].lastActivityAt").value("2026-10-01T00:00:00Z"));
    }

    @Test
    void givenAdministratorWhenReadingProgressThenOnlyGrantedArticlesAreReturned() throws Exception {
        jdbc.update("INSERT INTO public.article_answer_keys (blog_id, question_id, answer_text) "
                + "VALUES (3, 'q1', 'a'), (11, 'q1', 'b')");
        jdbc.update("INSERT INTO public.article_question_submissions "
                + "(blog_id, student_id, question_id, answer_text) VALUES (11, 2, 'q1', 'draft')");
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject) VALUES (3, '数学')");
        jdbc.update("INSERT INTO public.admin_subject_claim (admin_account_id, subject) VALUES (500, '数学')");
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        claimLearner(2, adminCookie, admin);

        mockMvc.perform(get("/api/v1/admin/learners/2/study-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].articleId").value(3))
                .andExpect(jsonPath("$[0].totalQuestions").value(1))
                .andExpect(jsonPath("$[0].submittedQuestions").value(0))
                .andExpect(jsonPath("$[0].pendingReviews").value(0));
    }

    @Test
    void givenUnclaimedLearnerWhenDeletingThenReturnsLearnerNotClaimedAndKeepsRow() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(delete("/api/v1/admin/learners/5")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("learner_not_claimed"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.student WHERE id = 5", Long.class)).isEqualTo(1);
    }

    @Test
    void givenSpecialTestLearnerWhenDeletingThenReturnsProtected() throws Exception {
        jdbc.update("""
                INSERT INTO public.student
                    (id, username, permissions, is_test, is_special_test, claimed_by, claimed_at)
                VALUES (?, ?, '{}'::integer[], true, true, 500, now())
                """, 8L, "test");
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(delete("/api/v1/admin/learners/8")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("special_test_learner_protected"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.student WHERE id = 8", Long.class)).isEqualTo(1);
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

    private void claimLearner(long learnerId, Cookie cookie, BrowserSession session) throws Exception {
        mockMvc.perform(put("/api/v1/admin/learners/" + learnerId + "/claim")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk());
    }

    private Cookie login(String username, String password) throws Exception {
        BrowserSession browser = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(result);
    }

    private Cookie requireCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("TUTOR_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void insertActiveAdministrator(long accountId, String username, String password) {
        jdbc.update("""
                INSERT INTO public.account
                    (id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, lower(?), ?, 'active', 'administrator', now())
                """, accountId, username, username, passwords.encode(password));
    }

    private void insertActiveLearner(long accountId, long learnerId, String username, String password) {
        jdbc.update("""
                INSERT INTO public.account
                    (id, learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, ?, lower(?), ?, 'active', 'learner', now())
                """, accountId, learnerId, username, username, passwords.encode(password));
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
