package com.tutorbase.administration.learner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=test-learner-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class TestLearnerHttpContractTest {

    private static final long ADMIN_ACCOUNT_ID = 500L;
    private static final long ALICE_ID = 2L;
    private static final long TEST_LEARNER_ID = 3L;
    private static final long PLAIN_TEST_LEARNER_ID = 4L;

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
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .build();
        jdbc.update("DELETE FROM public.wrong_questions");
        jdbc.update("DELETE FROM public.article_question_submissions");
        jdbc.update("DELETE FROM public.article_answer_keys");
        jdbc.update("DELETE FROM public.daily_check_in");
        jdbc.update("DELETE FROM public.user_points");
        jdbc.update("DELETE FROM public.card_collection");
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("UPDATE public.student SET claimed_by = NULL, claimed_at = NULL");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("""
                INSERT INTO public.student
                    (id, username, permissions, is_test, is_special_test)
                VALUES (2, 'alice', '{101}'::integer[], false, false)
                """);
        jdbc.update("""
                INSERT INTO public.student
                    (id, username, permissions, is_test, is_special_test)
                VALUES (3, 'tester', '{}'::integer[], true, true)
                """);
        jdbc.update("""
                INSERT INTO public.student
                    (id, username, permissions, is_test, is_special_test)
                VALUES (4, 'plain-tester', '{102}'::integer[], true, false)
                """);
        jdbc.update("""
                INSERT INTO public.courseware_catalog (article_id, subject, title)
                VALUES (101, '化学', '原子结构'), (102, '化学', '元素周期表')
                """);
        insertActiveAdministrator(ADMIN_ACCOUNT_ID, "admin", "Admin-password-2026");
        insertActiveLearner(1001L, ALICE_ID, "alice", "Learner-password-2026");
        insertActiveLearner(1002L, TEST_LEARNER_ID, "tester", "Tester-password-2026");
        jdbc.update("""
                INSERT INTO public.admin_subject_claim (admin_account_id, subject)
                VALUES (500, '化学')
                """);
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
    void givenAdministratorWhenCreatingLearnerThenCreatedLearnerIsTestLearner() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(get("/api/v1/admin/learners").param("limit", "10").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].learnerId").value(ALICE_ID))
                .andExpect(jsonPath("$.items[0].isTest").value(false))
                .andExpect(jsonPath("$.items[0].isSpecialTest").value(false))
                .andExpect(jsonPath("$.items[1].learnerId").value(TEST_LEARNER_ID))
                .andExpect(jsonPath("$.items[1].isTest").value(true))
                .andExpect(jsonPath("$.items[1].isSpecialTest").value(true))
                .andExpect(jsonPath("$.items[2].learnerId").value(PLAIN_TEST_LEARNER_ID))
                .andExpect(jsonPath("$.items[2].isTest").value(true))
                .andExpect(jsonPath("$.items[2].isSpecialTest").value(false));

        mockMvc.perform(post("/api/v1/admin/learners")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isTest").value(true))
                .andExpect(jsonPath("$.isSpecialTest").value(false));
    }

    @Test
    void givenSpecialTestLearnerWhenReadingContentGrantsThenReturnsAllCatalogArticles() throws Exception {
        Cookie testerCookie = login("tester", "Tester-password-2026");

        mockMvc.perform(get("/api/v1/me/content-grants").cookie(testerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleIds.length()").value(2))
                .andExpect(jsonPath("$.articleIds[0]").value(101))
                .andExpect(jsonPath("$.articleIds[1]").value(102))
                .andExpect(jsonPath("$.allArticles").value(true));

        // 未显式授权的文章，特殊测试学员仍可读取学习状态。
        mockMvc.perform(get("/api/v1/articles/102/study-state").cookie(testerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(102))
                .andExpect(jsonPath("$.questions").isArray());
    }

    @Test
    void givenNormalLearnerWhenReadingUngrantedArticleThenAccessIsDenied() throws Exception {
        Cookie aliceCookie = login("alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/articles/102/study-state").cookie(aliceCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("content_access_denied"));
    }

    @Test
    void givenSpecialTestLearnerWhenDrawingCardsThenFixedPointsDoNotDecrease() throws Exception {
        Cookie testerCookie = login("tester", "Tester-password-2026");
        BrowserSession tester = csrf(testerCookie);

        mockMvc.perform(get("/api/v1/rewards").cookie(testerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(999999999))
                .andExpect(jsonPath("$.lifetimePoints").value(999999999))
                .andExpect(jsonPath("$.stats.totalDraws").value(0));

        // 无 user_points 行时也应能抽卡（upsert），且返回固定高积分。
        mockMvc.perform(post("/api/v1/rewards/draws")
                        .cookie(testerCookie)
                        .header("X-CSRF-TOKEN", tester.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(999999999))
                .andExpect(jsonPath("$.lifetimePoints").value(999999999))
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.stats.totalDraws").value(1));

        mockMvc.perform(get("/api/v1/rewards").cookie(testerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(999999999))
                .andExpect(jsonPath("$.stats.totalDraws").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT points FROM public.user_points WHERE student_id = ?",
                Long.class,
                TEST_LEARNER_ID)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT draw_count FROM public.user_points WHERE student_id = ?",
                Long.class,
                TEST_LEARNER_ID)).isEqualTo(1);
    }

    @Test
    void givenNormalLearnerWhenDrawingCardsThenPointsAreDeductedNormally() throws Exception {
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points, draw_count)
                VALUES (?, 1000, 1000, 0)
                """, ALICE_ID);
        Cookie aliceCookie = login("alice", "Learner-password-2026");
        BrowserSession alice = csrf(aliceCookie);

        mockMvc.perform(post("/api/v1/rewards/draws")
                        .cookie(aliceCookie)
                        .header("X-CSRF-TOKEN", alice.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(920));

        jdbc.update("UPDATE public.user_points SET points = 0 WHERE student_id = ?", ALICE_ID);
        mockMvc.perform(post("/api/v1/rewards/draws")
                        .cookie(aliceCookie)
                        .header("X-CSRF-TOKEN", alice.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("insufficient_points"));
    }

    @Test
    void givenTestLearnerSubmissionsWhenReadingAnalyticsThenTestLearnersAreExcluded() throws Exception {
        jdbc.update("""
                INSERT INTO public.article_question_submissions
                    (blog_id, student_id, question_id, answer_text, review_status, submitted_at)
                VALUES (101, 2, 'q1', 'a', 'pending', now()),
                       (101, 3, 'q1', 'b', 'pending', now()),
                       (102, 4, 'q1', 'c', 'pending', now())
                """);
        Cookie adminCookie = login("admin", "Admin-password-2026");

        mockMvc.perform(get("/api/v1/admin/analytics/overview").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learners").value(1))
                .andExpect(jsonPath("$.claimedLearners").value(0))
                .andExpect(jsonPath("$.unclaimedLearners").value(1))
                .andExpect(jsonPath("$.activeLearners7d").value(1))
                .andExpect(jsonPath("$.pendingReviews").value(1))
                .andExpect(jsonPath("$.submissions7d").value(1));

        mockMvc.perform(get("/api/v1/admin/analytics/activity").param("days", "7").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[6].submissions").value(1))
                .andExpect(jsonPath("$.points[6].reviews").value(0));

        // profile 不排除测试学员，仍可查看其详情。
        mockMvc.perform(get("/api/v1/admin/learners/3/profile").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerId").value(3))
                .andExpect(jsonPath("$.username").value("tester"));
    }

    @Test
    void givenCatalogSyncWhenSpecialTestLearnerThenPermissionsIncludeAllCatalogArticles() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        mockMvc.perform(post("/api/v1/admin/courseware-catalog/sync")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                                Map.of("articleId", 101, "subject", "化学", "title", "原子结构"),
                                Map.of("articleId", 102, "subject", "化学", "title", "元素周期表"),
                                Map.of("articleId", 103, "subject", "化学", "title", "化学键"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(3));

        assertThat(jdbc.queryForObject(
                "SELECT permissions::text FROM public.student WHERE id = ?",
                String.class,
                TEST_LEARNER_ID)).isEqualTo("{101,102,103}");

        Cookie testerCookie = login("tester", "Tester-password-2026");
        mockMvc.perform(get("/api/v1/me/content-grants").cookie(testerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleIds.length()").value(3))
                .andExpect(jsonPath("$.articleIds[2]").value(103));
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
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
                    (id, learner_id, username, username_normalized, password_hash,
                     status, role, activated_at)
                VALUES (?, ?, ?, lower(?), ?, 'active', 'learner', now())
                """, accountId, learnerId, username, username, passwords.encode(password));
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
