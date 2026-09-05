package com.tutorbase.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
        "tutor.identity.csrf-secret=check-in-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
@Import(CheckInHttpContractTest.FixedClockConfiguration.class)
class CheckInHttpContractTest {

    /** 固定「今日」为 2026-08-20T02:00:00Z，即 Asia/Shanghai 的 2026-08-20 10:00。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-20T02:00:00Z"), ZoneOffset.UTC);
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
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc.update("DELETE FROM public.daily_check_in");
        jdbc.update("DELETE FROM public.user_points");
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
    void learnerCheckInAwardsBasePointsAndReflectsInMonthQuery() throws Exception {
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(post("/api/v1/checkins").cookie(learnerCookie).header("X-CSRF-TOKEN", csrf(learnerCookie).csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-20"))
                .andExpect(jsonPath("$.pointsAwarded").value(100))
                .andExpect(jsonPath("$.bonusDays").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.monthCount").value(1))
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false))
                .andExpect(jsonPath("$.totalPoints").value(100));

        assertThat(jdbc.queryForObject(
                "SELECT points FROM public.user_points WHERE student_id = 5", Long.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.daily_check_in "
                        + "WHERE student_id = 5 AND check_in_date = DATE '2026-08-20' AND bonus_days IS NULL",
                Long.class)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/checkins?year=2026&month=8").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(8))
                .andExpect(jsonPath("$.dates[0]").value("2026-08-20"))
                .andExpect(jsonPath("$.monthCount").value(1))
                .andExpect(jsonPath("$.todayCheckedIn").value(true))
                .andExpect(jsonPath("$.today").value("2026-08-20"))
                .andExpect(jsonPath("$.pointsPerCheckIn").value(100))
                .andExpect(jsonPath("$.bonusMilestones[0]").value(7))
                .andExpect(jsonPath("$.bonusMilestones[1]").value(14));
    }

    @Test
    void seventhCheckInOfMonthAwardsBonusPoints() throws Exception {
        seedCheckIns(5, "2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06");
        jdbc.update("INSERT INTO public.user_points (student_id, points) VALUES (5, 600)");
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(post("/api/v1/checkins").cookie(learnerCookie).header("X-CSRF-TOKEN", csrf(learnerCookie).csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-20"))
                .andExpect(jsonPath("$.pointsAwarded").value(200))
                .andExpect(jsonPath("$.bonusDays").value(7))
                .andExpect(jsonPath("$.monthCount").value(7))
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false))
                .andExpect(jsonPath("$.totalPoints").value(800));

        assertThat(jdbc.queryForObject(
                "SELECT points FROM public.user_points WHERE student_id = 5", Long.class)).isEqualTo(800);
        assertThat(jdbc.queryForObject(
                "SELECT bonus_days FROM public.daily_check_in "
                        + "WHERE student_id = 5 AND check_in_date = DATE '2026-08-20'",
                Integer.class)).isEqualTo(7);
    }

    @Test
    void repeatedCheckInIsIdempotentAndAwardsPointsOnce() throws Exception {
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");
        var browser = csrf(learnerCookie);

        mockMvc.perform(post("/api/v1/checkins").cookie(learnerCookie).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false));

        mockMvc.perform(post("/api/v1/checkins").cookie(learnerCookie).header("X-CSRF-TOKEN", browser.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-20"))
                .andExpect(jsonPath("$.pointsAwarded").value(100))
                .andExpect(jsonPath("$.monthCount").value(1))
                .andExpect(jsonPath("$.alreadyCheckedIn").value(true))
                .andExpect(jsonPath("$.totalPoints").value(100));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.daily_check_in WHERE student_id = 5", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT points FROM public.user_points WHERE student_id = 5", Long.class)).isEqualTo(100);
    }

    @Test
    void monthQueryIsScopedToRequestedMonth() throws Exception {
        seedCheckIns(5, "2026-07-15");
        seedCheckIns(5, "2026-08-01", "2026-08-05");
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/checkins?year=2026&month=7").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates.length()").value(1))
                .andExpect(jsonPath("$.dates[0]").value("2026-07-15"))
                .andExpect(jsonPath("$.monthCount").value(1))
                .andExpect(jsonPath("$.todayCheckedIn").value(false));

        mockMvc.perform(get("/api/v1/checkins?year=2026&month=8").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates.length()").value(2))
                .andExpect(jsonPath("$.dates[0]").value("2026-08-01"))
                .andExpect(jsonPath("$.dates[1]").value("2026-08-05"))
                .andExpect(jsonPath("$.monthCount").value(2))
                .andExpect(jsonPath("$.todayCheckedIn").value(false));
    }

    @Test
    void accountWithoutLearnerGets403LearnerContextRequired() throws Exception {
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (NULL, 'staff', 'staff', ?, 'active', 'learner', now())
                """, passwords.encode("Staff-password-2026"));
        Cookie staffCookie = login(csrf(null), "staff", "Staff-password-2026");

        mockMvc.perform(get("/api/v1/checkins?year=2026&month=8").cookie(staffCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("learner_context_required"));

        mockMvc.perform(post("/api/v1/checkins")
                        .cookie(staffCookie)
                        .header("X-CSRF-TOKEN", csrf(staffCookie).csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("learner_context_required"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/checkins?year=2026&month=8"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        // 未认证的 POST 在授权检查前先被会话 CSRF 过滤器拒绝
        mockMvc.perform(post("/api/v1/checkins"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    void invalidMonthParametersGet400ValidationFailed() throws Exception {
        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/checkins?year=2026&month=13").cookie(learnerCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(get("/api/v1/checkins?year=1999&month=8").cookie(learnerCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    /** 以基础 100 分、无奖励写入指定日期的签到种子行。 */
    private void seedCheckIns(long studentId, String... dates) {
        for (String date : dates) {
            jdbc.update("INSERT INTO public.daily_check_in (student_id, check_in_date, points_awarded, bonus_days) "
                            + "VALUES (?, CAST(? AS DATE), 100, NULL)",
                    studentId, date);
        }
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
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
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
