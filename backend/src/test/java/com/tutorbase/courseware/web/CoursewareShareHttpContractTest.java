package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 分享 → 资源目录 → 共享树 → 获取（复制）→ 我的获取关系。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-share-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareShareHttpContractTest {

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
        insertActiveAccount(2, "alpha", "administrator", "Alpha-password-2026");
        insertActiveAccount(5, "beta", "administrator", "Beta-password-2026");
        alphaTeacherId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('public.student', 'id'), 5, true)", Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.account', 'id'), "
                        + "(SELECT max(id) FROM public.account), true)",
                Long.class);
    }

    @Test
    void sharedSubjectCanBeListedExploredAndAcquiredOnce() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        publishRelease(alpha, csrf(alpha).csrfToken()).andExpect(status().isCreated());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        String betaCsrf = csrf(beta).csrfToken();

        shareSubject(alpha, csrf(alpha).csrfToken(), true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scope").value("subject"))
                .andExpect(jsonPath("$[0].allowCopy").value(true));

        mockMvc.perform(get("/api/v1/admin/courseware/resources").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherId").value(alphaTeacherId))
                .andExpect(jsonPath("$[0].subject").value("高中化学"))
                .andExpect(jsonPath("$[0].wholeSubject").value(true))
                .andExpect(jsonPath("$[0].articleCount").value(2));

        mockMvc.perform(get("/api/v1/admin/courseware/resources/tree")
                        .param("teacherId", Long.toString(alphaTeacherId))
                        .param("subject", "高中化学")
                        .cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].allowCopy").value(true));

        MvcResult acquired = mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].alreadyExists").value(false))
                .andReturn();
        long firstLocalId = objectMapper.readTree(acquired.getResponse().getContentAsString())
                .get(0).get("localArticleId").asLong();
        assertThat(firstLocalId).isGreaterThanOrEqualTo(1000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_draft", Long.class)).isEqualTo(2L);

        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alreadyExists").value(true));

        mockMvc.perform(get("/api/v1/admin/courseware/copies").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hasUpdate").value(false))
                .andExpect(jsonPath("$[0].sourceTeacherName").value("alpha"));
    }

    @Test
    void copyDeniedWhenSourceDisallowsCopyAndShareRevocationClosesTree() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        String alphaCsrf = csrf(alpha).csrfToken();
        publishRelease(alpha, alphaCsrf).andExpect(status().isCreated());
        shareSubject(alpha, alphaCsrf, true, false).andExpect(status().isOk());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", csrf(beta).csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学",
                                "articleIds", List.of(3)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_source_forbidden"));

        long shareId = jdbc.queryForObject(
                "SELECT id FROM public.course_share WHERE owner_teacher_account_id = ?",
                Long.class, alphaTeacherId);
        mockMvc.perform(delete("/api/v1/admin/courseware/shares/{shareId}", shareId)
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", csrf(alpha).csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/courseware/resources/tree")
                        .param("teacherId", Long.toString(alphaTeacherId))
                        .param("subject", "高中化学")
                        .cookie(beta))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("courseware_not_found"));
    }

    @Test
    void cannotShareUnpublishedSubject() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        shareSubject(alpha, csrf(alpha).csrfToken(), true, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("courseware_validation_failed"));
    }

    @Test
    void previewFollowsAllowPreviewFlag() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        String alphaCsrf = csrf(alpha).csrfToken();
        publishRelease(alpha, alphaCsrf).andExpect(status().isCreated());
        shareSubject(alpha, alphaCsrf, true, false).andExpect(status().isOk());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        mockMvc.perform(get("/api/v1/admin/courseware/resources/articles/{teacherId}/{articleId}/preview",
                        alphaTeacherId, 3)
                        .cookie(beta))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("【题干1】题一")));

        shareSubject(alpha, alphaCsrf, false, false).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/courseware/resources/articles/{teacherId}/{articleId}/preview",
                        alphaTeacherId, 3)
                        .cookie(beta))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_source_forbidden"));
    }

    @Test
    void renameAndConflictStrategyOnAcquire() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        String alphaCsrf = csrf(alpha).csrfToken();
        publishRelease(alpha, alphaCsrf).andExpect(status().isCreated());
        shareSubject(alpha, alphaCsrf, true, true).andExpect(status().isOk());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        String betaCsrf = csrf(beta).csrfToken();
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学",
                                "articleIds", List.of(3)))))
                .andExpect(status().isOk());

        long copyId = jdbc.queryForObject(
                "SELECT id FROM public.course_copy WHERE source_article_id = 3", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/courseware/copies/{copyId}", copyId)
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "我的原电池",
                                "logicalPath", "高中化学/理论课讲义/3-2 电解池.md"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("我的原电池"))
                .andExpect(jsonPath("$.localLogicalPath").value("高中化学/理论课讲义/3-2 电解池.md"));

        // 与本地已存在的路径同名：默认 409
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学",
                                "articleIds", List.of(9)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courseware_target_exists"));

        // rename 策略：自动加后缀
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId,
                                "subject", "高中化学",
                                "articleIds", List.of(9),
                                "conflictStrategy", "rename"))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT local_logical_path FROM public.course_copy WHERE source_article_id = 9",
                String.class)).contains("（2）.md");
    }

    @Test
    void notificationsCoverAcquireUpdateAndRevoke() throws Exception {
        Cookie alpha = login(csrf(null), "alpha", "Alpha-password-2026");
        String alphaCsrf = csrf(alpha).csrfToken();
        publishRelease(alpha, alphaCsrf).andExpect(status().isCreated());
        shareSubject(alpha, alphaCsrf, true, true).andExpect(status().isOk());

        Cookie beta = login(csrf(null), "beta", "Beta-password-2026");
        String betaCsrf = csrf(beta).csrfToken();
        mockMvc.perform(post("/api/v1/admin/courseware/copies")
                        .cookie(beta)
                        .header("X-CSRF-TOKEN", betaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceTeacherId", alphaTeacherId, "subject", "高中化学"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/courseware/notifications").cookie(alpha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("share_acquired"));

        mockMvc.perform(post("/api/v1/admin/courseware/notifications/read")
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", alphaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", alphaCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "articles", List.of(
                                        article(3L, "3-1 原电池", "【题干1】改版\n\n【@1】\n"),
                                        article(9L, "3-2 电解池", "【题干1】题二\n\n【@1】\n"))))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/courseware/notifications")
                        .param("unreadOnly", "true")
                        .cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("source_updated"));

        long shareId = jdbc.queryForObject(
                "SELECT id FROM public.course_share WHERE owner_teacher_account_id = ?",
                Long.class, alphaTeacherId);
        mockMvc.perform(delete("/api/v1/admin/courseware/shares/{shareId}", shareId)
                        .cookie(alpha)
                        .header("X-CSRF-TOKEN", csrf(alpha).csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/courseware/notifications").cookie(beta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.items[0].kind").value("share_revoked"));
    }

    private ResultActions shareSubject(Cookie cookie, String csrfToken, boolean allowPreview, boolean allowCopy)
            throws Exception {
        return mockMvc.perform(post("/api/v1/admin/courseware/shares")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subject", "高中化学",
                        "scope", "subject",
                        "allowPreview", allowPreview,
                        "allowCopy", allowCopy))));
    }

    private ResultActions publishRelease(Cookie cookie, String csrfToken) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/courseware/releases")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subject", "高中化学",
                        "articles", List.of(
                                article(3L, "3-1 原电池", "【题干1】题一\n\n【@1】\n"),
                                article(9L, "3-2 电解池", "【题干1】题二\n\n【@1】\n"))))));
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

    private void insertActiveAccount(long learnerId, String username, String role, String password) {
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, lower(?), ?, 'active', ?, now())
                """, learnerId, username, username, passwords.encode(password), role);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("courseware-share-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
