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
 * 来源更新链路：汇总 → 单篇 diff → 覆盖（先备份）/ 忽略，以及乐观锁 409。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-update-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareUpdateHttpContractTest {

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

    private long alphaTeacherId;

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
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'alpha', '{}')");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'beta', '{}')");
        insertActiveAccount(2, "alpha", "Alpha-password-2026");
        insertActiveAccount(5, "beta", "Beta-password-2026");
        alphaTeacherId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('public.student', 'id'), 5, true)", Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.account', 'id'), "
                        + "(SELECT max(id) FROM public.account), true)",
                Long.class);
    }

    @Test
    void detectsAppliesAndDismissesSourceUpdates() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        publish(alpha, csrf(alpha).csrfToken(), "【题干1】旧版题干\n\n【@1】\n");
        shareAndAcquire(alpha);

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        String betaCsrf = csrf(beta).csrfToken();

        publish(alpha, csrf(alpha).csrfToken(), "【题干1】新版题干\n\n【@1】\n");

        mockMvc.perform(get("/api/v1/admin/courseware/updates").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.updated[0].title").value("3-1 原电池"));

        long copyId = jdbc.queryForObject(
                "SELECT id FROM public.course_copy WHERE owner_account_id = "
                        + "(SELECT id FROM public.account WHERE username = 'beta')", Long.class);

        MvcResult detail = mockMvc.perform(get("/api/v1/admin/courseware/copies/{copyId}/updates", copyId)
                        .cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localModified").value(false))
                .andReturn();
        String sourceHash = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("sourceHash").asText();
        String diff = objectMapper.readTree(detail.getResponse().getContentAsString()).get("diff").asText();
        assertThat(diff).contains("-【题干1】旧版题干").contains("+【题干1】新版题干");

        mockMvc.perform(post("/api/v1/admin/courseware/copies/{copyId}/updates/apply", copyId)
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "overwrite",
                                "expectedSourceHash", "sha256:" + "0".repeat(64)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courseware_hash_conflict"));

        mockMvc.perform(post("/api/v1/admin/courseware/copies/{copyId}/updates/apply", copyId)
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "overwrite",
                                "expectedSourceHash", sourceHash))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.backupHash").value(org.hamcrest.Matchers.startsWith("sha256:")));

        String draft = jdbc.queryForObject(
                "SELECT markdown FROM public.course_draft WHERE article_id = "
                        + "(SELECT local_article_id FROM public.course_copy WHERE id = " + copyId + ")",
                String.class);
        assertThat(draft).contains("新版题干");

        mockMvc.perform(get("/api/v1/admin/courseware/updates").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));

        publish(alpha, csrf(alpha).csrfToken(), "【题干1】第三版\n\n【@1】\n");
        mockMvc.perform(get("/api/v1/admin/courseware/updates").cookie(beta))
                .andExpect(jsonPath("$.updatedCount").value(1));

        mockMvc.perform(post("/api/v1/admin/courseware/copies/{copyId}/updates/dismiss", copyId)
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false));

        mockMvc.perform(get("/api/v1/admin/courseware/updates").cookie(beta))
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    @Test
    void reportsSourceArticleRemovalAsRemoved() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        String alphaCsrf = csrf(alpha).csrfToken();
        publish(alpha, alphaCsrf, "【题干1】保留\n\n【@1】\n");
        shareAndAcquire(alpha);

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        // 来源发布一个不再包含该篇的版本（只保留另一篇）
        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", alphaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "articles", List.of(article(9L, "3-2 电解池", "【题干1】另一篇\n\n【@1】\n"))))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/courseware/updates").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedCount").value(1))
                .andExpect(jsonPath("$.removed[0].sourceArticleId").value(3));
    }

    private void shareAndAcquire(Cookie alpha) throws Exception {
        mockMvc.perform(post("/api/v1/admin/courseware/shares")
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", csrf(alpha).csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学", "scope", "subject",
                                "allowPreview", true, "allowCopy", true))))
                .andExpect(status().isOk());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", csrf(beta).csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId, "subject", "高中化学"))))
                .andExpect(status().isOk());
    }

    private ResultActions publish(Cookie cookie, String csrfToken, String markdown) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/courseware/releases")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subject", "高中化学",
                        "articles", List.of(article(3L, "3-1 原电池", markdown))))));
    }

    private Map<String, Object> article(Long articleId, String title, String markdown) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("articleId", articleId);
        article.put("legacyArticleId", articleId);
        article.put("title", title);
        article.put("series", "高中化学");
        article.put("logicalPath", "高中化学/理论课讲义/" + title + ".md");
        article.put("markdown", markdown);
        article.put("answers", List.of(Map.of("questionId", "1", "answer", "A", "autoGrade", true)));
        return article;
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
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("TUTOR_SESSION");
    }

    private void insertActiveAccount(long learnerId, String username, String password) {
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, lower(?), ?, 'active', 'administrator', now())
                """, learnerId, username, username, passwords.encode(password));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("courseware-update-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
