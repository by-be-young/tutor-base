package com.tutorbase.administration.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.tutorbase.shared.http.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
        "tutor.identity.csrf-secret=admin-analytics-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
@Import(AdminAnalyticsHttpContractTest.FixedClockConfiguration.class)
class AdminAnalyticsHttpContractTest {

    /** 固定「今日」为 2026-10-29T02:00:00Z，即 Asia/Shanghai 的 2026-10-29 10:00。 */
    private static final Instant FIXED_NOW = Instant.parse("2026-10-29T02:00:00Z");
    private static final long ADMIN_ACCOUNT_ID = 500L;
    private static final long ADMIN2_ACCOUNT_ID = 501L;
    private static final long ALICE_ID = 2L;
    private static final long BOB_ID = 5L;
    private static final long CHEMISTRY_ARTICLE_ONE = 101L;
    private static final long CHEMISTRY_ARTICLE_TWO = 102L;
    private static final long ENGLISH_ARTICLE = 201L;
    private static final long PHYSICS_ARTICLE = 301L;
    private static final String CHEMISTRY = "化学";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

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
        jdbc.update("DELETE FROM public.wrong_questions");
        jdbc.update("DELETE FROM public.article_question_submissions");
        jdbc.update("DELETE FROM public.article_answer_keys");
        jdbc.update("DELETE FROM public.daily_check_in");
        jdbc.update("DELETE FROM public.user_points");
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("UPDATE public.student SET claimed_by = NULL, claimed_at = NULL");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("""
                INSERT INTO public.student (id, username, permissions)
                VALUES (2, 'alice', '{101,102,201,301}'::integer[])
                """);
        jdbc.update("""
                INSERT INTO public.student (id, username, permissions)
                VALUES (5, 'bob', '{}'::integer[])
                """);
        insertActiveLearner(1001L, ALICE_ID, "alice", "Learner-password-2026");
        insertActiveAdministrator(ADMIN_ACCOUNT_ID, "admin", "Admin-password-2026");
        insertActiveAdministrator(ADMIN2_ACCOUNT_ID, "admin2", "Admin2-password-2026");
        jdbc.update("""
                INSERT INTO public.courseware_catalog (article_id, subject, title)
                VALUES (101, '化学', '原子结构'), (102, '化学', '元素周期表'),
                       (201, '英语', '时态'), (301, '物理', '力学')
                """);
        jdbc.update("""
                INSERT INTO public.admin_subject_claim (admin_account_id, subject)
                VALUES (500, '化学')
                """);
        jdbc.update("""
                INSERT INTO public.admin_courseware_claim (admin_account_id, article_id)
                VALUES (501, 201)
                """);
        jdbc.update("UPDATE public.student SET claimed_by = 500, claimed_at = now() WHERE id = 2");
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
    void givenNoAuthenticationWhenReadingAnalyticsThenReturnsUnauthenticated() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                get("/api/v1/admin/analytics/overview"),
                get("/api/v1/admin/analytics/activity"),
                get("/api/v1/admin/learners/2/profile")}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("unauthenticated"));
        }
    }

    @Test
    void givenNonAdministratorWhenReadingAnalyticsThenReturnsForbidden() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                get("/api/v1/admin/analytics/overview"),
                get("/api/v1/admin/analytics/activity"),
                get("/api/v1/admin/learners/2/profile")}) {
            mockMvc.perform(request.with(user("learner").roles("LEARNER")))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("forbidden"));
        }
    }

    @Test
    void givenAdministratorWhenReadingOverviewThenCountsOnlyVisibleCoursewareData() throws Exception {
        insertPendingSubmission(CHEMISTRY_ARTICLE_ONE, ALICE_ID, "q1", "now() - interval '1 day'");
        insertPendingSubmission(CHEMISTRY_ARTICLE_TWO, ALICE_ID, "q1", "now() - interval '2 days'");
        insertPendingSubmission(ENGLISH_ARTICLE, ALICE_ID, "q1", "now() - interval '1 day'");
        insertPendingSubmission(PHYSICS_ARTICLE, ALICE_ID, "q1", "now() - interval '1 day'");
        jdbc.update("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text,
                     review_status, review_result, submitted_at, reviewed_at)
                VALUES (102, 2, 'q2', 'old', 'reviewed', 'correct',
                        now() - interval '30 days', now() - interval '29 days')
                """);
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/analytics/overview").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.learners").value(2))
                .andExpect(jsonPath("$.claimedLearners").value(1))
                .andExpect(jsonPath("$.unclaimedLearners").value(1))
                .andExpect(jsonPath("$.activeLearners7d").value(1))
                .andExpect(jsonPath("$.pendingReviews").value(2))
                .andExpect(jsonPath("$.submissions7d").value(2))
                .andExpect(jsonPath("$.courseware.visible").value(2))
                .andExpect(jsonPath("$.courseware.unclaimed").value(1))
                .andExpect(jsonPath("$.courseware.subjects").value(1));
    }

    @Test
    void givenNoVisibleCoursewareWhenReadingOverviewThenSubmissionStatsAreZero() throws Exception {
        insertPendingSubmission(CHEMISTRY_ARTICLE_ONE, ALICE_ID, "q1", "now() - interval '1 day'");
        jdbc.update("DELETE FROM public.admin_courseware_claim WHERE admin_account_id = 501");
        Cookie admin2Cookie = login("admin2", "Admin2-password-2026");

        mockMvc.perform(get("/api/v1/admin/analytics/overview").cookie(admin2Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learners").value(2))
                .andExpect(jsonPath("$.pendingReviews").value(0))
                .andExpect(jsonPath("$.submissions7d").value(0))
                .andExpect(jsonPath("$.courseware.visible").value(0))
                .andExpect(jsonPath("$.courseware.unclaimed").value(2))
                .andExpect(jsonPath("$.courseware.subjects").value(0));
    }

    @Test
    void givenActivityDataWhenReadingActivityThenDefaultsToThirtyDaysAndFillsMissingDates() throws Exception {
        insertSubmission(101, ALICE_ID, "q1", "pending", null,
                "'2026-10-29T01:00:00Z'", null, "'2026-10-29T01:00:00Z'");
        insertSubmission(102, ALICE_ID, "q1", "reviewed", "correct",
                "'2026-10-28T16:30:00Z'", "'2026-10-28T12:00:00Z'",
                "'2026-10-29T01:30:00Z'");
        insertSubmission(101, ALICE_ID, "q2", "pending", null,
                "'2026-10-27T10:00:00Z'", null, "'2026-10-27T10:00:00Z'");
        insertSubmission(ENGLISH_ARTICLE, ALICE_ID, "q1", "pending", null,
                "'2026-10-29T01:00:00Z'", null, "'2026-10-29T01:00:00Z'");
        insertSubmission(101, ALICE_ID, "q3", "pending", null,
                "'2026-10-20T01:00:00Z'", null, "'2026-10-20T01:00:00Z'");
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/analytics/activity").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.points.length()").value(30))
                .andExpect(jsonPath("$.points[0].date").value("2026-09-30"))
                .andExpect(jsonPath("$.points[0].submissions").value(0))
                .andExpect(jsonPath("$.points[0].reviews").value(0))
                .andExpect(jsonPath("$.points[20].date").value("2026-10-20"))
                .andExpect(jsonPath("$.points[20].submissions").value(1))
                .andExpect(jsonPath("$.points[27].date").value("2026-10-27"))
                .andExpect(jsonPath("$.points[27].submissions").value(1))
                .andExpect(jsonPath("$.points[28].date").value("2026-10-28"))
                .andExpect(jsonPath("$.points[28].submissions").value(0))
                .andExpect(jsonPath("$.points[28].reviews").value(1))
                .andExpect(jsonPath("$.points[29].date").value("2026-10-29"))
                .andExpect(jsonPath("$.points[29].submissions").value(2))
                .andExpect(jsonPath("$.points[29].reviews").value(0));
    }

    @Test
    void givenDaysParameterWhenReadingActivityThenHonoursWhitelistAndRejectsInvalidValues() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/analytics/activity").param("days", "7").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-23"))
                .andExpect(jsonPath("$.points[6].date").value("2026-10-29"));

        mockMvc.perform(get("/api/v1/admin/analytics/activity").param("days", "90").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(90))
                .andExpect(jsonPath("$.points.length()").value(90))
                .andExpect(jsonPath("$.points[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.points[89].date").value("2026-10-29"));

        for (String invalidDays : new String[] {"6", "91", "not-a-number"}) {
            mockMvc.perform(get("/api/v1/admin/analytics/activity")
                            .param("days", invalidDays)
                            .cookie(adminCookie))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("validation_failed"));
        }
    }

    @Test
    void givenClaimedLearnerWhenReadingProfileThenReturnsGlobalRewardsAndScopedProgress() throws Exception {
        jdbc.update("""
                INSERT INTO public.article_answer_keys (blog_id, question_id, answer_text)
                VALUES (101, 'q1', 'a'), (101, 'q2', 'b'), (102, 'q1', 'c'),
                       (201, 'q1', 'd'), (301, 'q1', 'e')
                """);
        insertSubmission(101, ALICE_ID, "q1", "pending", null,
                "'2026-10-29T01:00:00Z'", null, "'2026-10-29T02:00:00Z'");
        insertSubmission(101, ALICE_ID, "q2", "reviewed", "correct",
                "'2026-10-28T01:00:00Z'", "'2026-10-28T02:00:00Z'",
                "'2026-10-28T02:00:00Z'");
        insertSubmission(102, ALICE_ID, "q1", "pending", null,
                "'2026-10-27T01:00:00Z'", null, "'2026-10-27T02:00:00Z'");
        insertSubmission(ENGLISH_ARTICLE, ALICE_ID, "q1", "pending", null,
                "'2026-10-29T01:00:00Z'", null, "'2026-10-26T02:00:00Z'");
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points)
                VALUES (2, 120, 300)
                """);
        jdbc.update("""
                INSERT INTO public.daily_check_in
                    (student_id, check_in_date, points_awarded, created_at)
                VALUES (2, '2026-10-29', 100, '2026-10-25T00:00:00Z'),
                       (2, '2026-10-20', 100, '2026-10-25T00:00:00Z'),
                       (2, '2026-10-01', 100, '2026-10-25T00:00:00Z'),
                       (2, '2026-09-01', 100, '2026-09-01T00:00:00Z')
                """);
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/learners/2/profile").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.learnerId").value(2))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.claimedByAccountId").value(500))
                .andExpect(jsonPath("$.claimedByUsername").value("admin"))
                .andExpect(jsonPath("$.lastActivityAt").value("2026-10-29T02:00:00Z"))
                .andExpect(jsonPath("$.points").value(120))
                .andExpect(jsonPath("$.lifetimePoints").value(300))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.checkInDays30").value(3))
                .andExpect(jsonPath("$.subjectProgress.length()").value(1))
                .andExpect(jsonPath("$.subjectProgress[0].subject").value(CHEMISTRY))
                .andExpect(jsonPath("$.subjectProgress[0].totalQuestions").value(3))
                .andExpect(jsonPath("$.subjectProgress[0].submittedQuestions").value(3))
                .andExpect(jsonPath("$.subjectProgress[0].pendingReviews").value(2))
                .andExpect(jsonPath("$.recentSubmissions.length()").value(3))
                .andExpect(jsonPath("$.recentSubmissions[0].articleId").value(101))
                .andExpect(jsonPath("$.recentSubmissions[0].questionId").value("q1"))
                .andExpect(jsonPath("$.recentSubmissions[0].reviewStatus").value("pending"))
                .andExpect(jsonPath("$.recentSubmissions[0].reviewResult")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.recentSubmissions[0].submittedAt").value("2026-10-29T01:00:00Z"))
                .andExpect(jsonPath("$.recentSubmissions[1].articleId").value(101))
                .andExpect(jsonPath("$.recentSubmissions[1].questionId").value("q2"))
                .andExpect(jsonPath("$.recentSubmissions[1].reviewResult").value("correct"))
                .andExpect(jsonPath("$.recentSubmissions[2].articleId").value(102))
                .andExpect(jsonPath("$.activity.length()").value(30))
                .andExpect(jsonPath("$.activity[0].date").value("2026-09-30"))
                .andExpect(jsonPath("$.activity[0].submissions").value(0))
                .andExpect(jsonPath("$.activity[27].date").value("2026-10-27"))
                .andExpect(jsonPath("$.activity[27].submissions").value(1))
                .andExpect(jsonPath("$.activity[28].date").value("2026-10-28"))
                .andExpect(jsonPath("$.activity[28].submissions").value(1))
                .andExpect(jsonPath("$.activity[29].date").value("2026-10-29"))
                .andExpect(jsonPath("$.activity[29].submissions").value(1));
    }

    @Test
    void givenUnclaimedLearnerWhenReadingProfileThenReturnsLearnerNotClaimed() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/learners/5/profile").cookie(adminCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("learner_not_claimed"));
    }

    @Test
    void givenMissingLearnerWhenReadingProfileThenReturnsNotFound() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/learners/9999/profile").cookie(adminCookie))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    private void insertPendingSubmission(long articleId, long learnerId, String questionId,
                                         String submittedAtExpression) {
        jdbc.update("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text,
                     review_status, review_result, submitted_at, reviewed_at)
                VALUES (?, ?, ?, 'answer', 'pending', NULL, %s, NULL)
                """.formatted(submittedAtExpression), articleId, learnerId, questionId);
    }

    private void insertSubmission(long articleId, long learnerId, String questionId,
                                  String reviewStatus, String reviewResult,
                                  String submittedAtExpression, String reviewedAtExpression,
                                  String updatedAtExpression) {
        jdbc.update("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text,
                     review_status, review_result, submitted_at, reviewed_at, updated_at)
                VALUES (?, ?, ?, 'answer', ?, ?, %s, %s, %s)
                """.formatted(
                        submittedAtExpression,
                        reviewedAtExpression == null ? "NULL" : reviewedAtExpression,
                        updatedAtExpression),
                articleId, learnerId, questionId, reviewStatus, reviewResult);
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
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        return new BrowserSession(cookie, token);
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
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
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
                    (id, learner_id, username, username_normalized, password_hash,
                     status, role, activated_at)
                VALUES (?, ?, ?, lower(?), ?, 'active', 'learner', now())
                """, accountId, learnerId, username, username, passwords.encode(password));
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
