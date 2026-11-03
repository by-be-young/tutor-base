package com.tutorbase.courseware.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * 认领模型下线开关：`tutor.courseware.legacy-claims-enabled=false` 后写入返回 410，
 * 读取仍可用（旧页面不至于整页报错），允许运维平滑切换。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-claims-retired-secret",
        "tutor.identity.public-mutation-limit=100",
        "tutor.courseware.legacy-claims-enabled=false"
})
class CoursewareClaimsRetiredHttpContractTest {

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
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}')");
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (2, 'young', 'young', ?, 'active', 'administrator', now())
                """, passwords.encode("Admin-password-2026"));
    }

    @Test
    void claimWritesAreGoneButReadsStillWork() throws Exception {
        Cookie cookie = login(csrf(null), "young", "Admin-password-2026");
        String csrf = csrf(cookie).csrfToken();

        mockMvc.perform(put("/api/v1/admin/courseware-claims/subjects/{subject}", "数学")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("courseware_claims_retired"));

        mockMvc.perform(delete("/api/v1/admin/courseware-claims/subjects/{subject}", "数学")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isGone());

        mockMvc.perform(put("/api/v1/admin/courseware-claims/articles/{articleId}", 10)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isGone());

        mockMvc.perform(delete("/api/v1/admin/courseware-claims/articles/{articleId}", 10)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isGone());

        mockMvc.perform(post("/api/v1/admin/courseware-claims/articles/{articleId}/abandonment", 10)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isGone());

        mockMvc.perform(post("/api/v1/admin/courseware-catalog/sync")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", java.util.List.of(Map.of("articleId", 10, "subject", "数学"))))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("courseware_claims_retired"));

        mockMvc.perform(get("/api/v1/admin/courseware-claims").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectClaims").isEmpty());
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = existingCookie == null ? result.getResponse().getCookie("TUTOR_SESSION") : existingCookie;
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        return new BrowserSession(cookie, token);
    }

    private Cookie login(BrowserSession browser, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("TUTOR_SESSION");
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
