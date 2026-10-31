package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 运维：用量、孤儿 blob 回收、配额超限 413。
 *
 * <p>配额调到 1200 字节，让"两篇正常课件"能发布、"再加一篇大课件"被拒，避免用巨型 payload 测。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-ops-contract-test-secret",
        "tutor.identity.public-mutation-limit=100",
        "tutor.courseware.quota-bytes=3000"
})
class CoursewareMaintenanceHttpContractTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("tutor.courseware.root", () -> STORAGE_ROOT.toString());
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
        jdbc.update("DELETE FROM public.course_copy");
        jdbc.update("DELETE FROM public.course_draft");
        jdbc.update("DELETE FROM public.course_share");
        jdbc.update("DELETE FROM public.course_article");
        jdbc.update("DELETE FROM public.course_release_head");
        jdbc.update("DELETE FROM public.course_release");
        jdbc.update("DELETE FROM public.course_operation_log");
        jdbc.update("DELETE FROM public.course_notification");
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
    void usageAndGarbageCollectionKeepReferencedBlobs() throws Exception {
        Cookie cookie = login(csrf(null), "young", "Admin-password-2026");
        String csrf = csrf(cookie).csrfToken();
        publish(cookie, csrf, "【题干1】第一版\n\n【@1】\n").andExpect(status().isCreated());
        publish(cookie, csrf, "【题干1】第二版\n\n【@1】\n").andExpect(status().isCreated());

        MvcResult usage = mockMvc.perform(get("/api/v1/admin/courseware/maintenance/usage").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotaBytes").value(3000))
                .andExpect(jsonPath("$.orphanCount").value(0))
                .andReturn();
        long totalBytes = objectMapper.readTree(usage.getResponse().getContentAsString())
                .get("totalBytes").asLong();
        assertThat(totalBytes).isPositive();

        // 历史版本的正文仍被 course_article 引用，不该被回收
        mockMvc.perform(post("/api/v1/admin/courseware/maintenance/gc").cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.removedCount").value(0));
    }

    @Test
    void publishRejectsWhenQuotaExceeded() throws Exception {
        Cookie cookie = login(csrf(null), "young", "Admin-password-2026");
        String csrf = csrf(cookie).csrfToken();

        publish(cookie, csrf, "【题干1】小课件\n\n【@1】\n").andExpect(status().isCreated());

        StringBuilder huge = new StringBuilder("【题干1】");
        huge.append("字".repeat(4000));
        huge.append("\n\n【@1】\n");
        publish(cookie, csrf, huge.toString())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("courseware_quota_exceeded"));
    }

    private ResultActions publish(Cookie cookie, String csrfToken, String markdown) throws Exception {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("articleId", 9);
        article.put("legacyArticleId", 9);
        article.put("title", "3-1 原电池");
        article.put("series", "高中化学");
        article.put("logicalPath", "高中化学/理论课讲义/3-1 原电池.md");
        article.put("markdown", markdown);
        article.put("answers", List.of(Map.of("questionId", "1", "answer", "A", "autoGrade", true)));
        return mockMvc.perform(post("/api/v1/admin/courseware/releases")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subject", "高中化学", "articles", List.of(article)))));
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

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("courseware-ops-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
