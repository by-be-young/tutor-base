package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * 课件托管 P0 契约：发布 → 学员清单 → 取正文，含版本冲突、校验失败与越权。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-release-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareReleaseHttpContractTest {

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

    private long administratorAccountId;

    @BeforeEach
    void configureMockMvcAndData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(traceIdFilter)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc.update("DELETE FROM public.course_article");
        jdbc.update("DELETE FROM public.course_release_head");
        jdbc.update("DELETE FROM public.course_release");
        jdbc.update("DELETE FROM public.course_operation_log");
        jdbc.update("DELETE FROM public.student_teacher");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}')");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{3}')");
        insertActiveAccount(2, "young", "administrator", "Admin-password-2026");
        insertActiveAccount(5, "Alice", "learner", "Learner-password-2026");
        administratorAccountId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'young'", Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.student', 'id'), 5, true)", Long.class);
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('public.account', 'id'), "
                        + "(SELECT max(id) FROM public.account), true)",
                Long.class);
    }

    @Test
    void administratorPublishesImmutableReleaseAndReadsOwnContent() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        MvcResult published = publish(adminCookie, admin.csrfToken(), null, validArticles())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(administratorAccountId))
                .andExpect(jsonPath("$.subject").value("高中化学"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.articleCount").value(2))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andReturn();

        String manifestHash = objectMapper.readTree(published.getResponse().getContentAsString())
                .get("contentHash").asText();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.course_release WHERE teacher_account_id = ?",
                Long.class, administratorAccountId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT r.version FROM public.course_release_head AS h "
                        + "JOIN public.course_release AS r ON r.id = h.release_id "
                        + "WHERE h.teacher_account_id = ? AND h.subject = '高中化学'",
                Integer.class, administratorAccountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT manifest_hash FROM public.course_release WHERE teacher_account_id = ?",
                String.class, administratorAccountId)).isEqualTo(manifestHash);

        mockMvc.perform(get("/api/v1/courseware/articles/{teacherId}/{articleId}/content",
                        administratorAccountId, 3)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "markdown")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("【题干1】")));
    }

    @Test
    void learnerSeesManifestsOnlyAfterLinkAndGrant() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        publish(adminCookie, csrf(adminCookie).csrfToken(), null, validArticles())
                .andExpect(status().isCreated());

        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/courseware/manifests").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        jdbc.update("INSERT INTO public.student_teacher (student_id, teacher_account_id, is_primary) "
                + "VALUES (5, ?, true)", administratorAccountId);

        mockMvc.perform(get("/api/v1/courseware/manifests").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].teacherId").value(administratorAccountId))
                .andExpect(jsonPath("$.items[0].version").value(1))
                .andExpect(jsonPath("$.items[0].articles.length()").value(1))
                .andExpect(jsonPath("$.items[0].articles[0].articleId").value(3));

        mockMvc.perform(get("/api/v1/courseware/articles/{teacherId}/{articleId}/content",
                        administratorAccountId, 3)
                        .cookie(learnerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courseware/articles/{teacherId}/{articleId}/content",
                        administratorAccountId, 9)
                        .cookie(learnerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_forbidden"));
    }

    @Test
    void publishRejectsStaleExpectVersionAndAcceptsMatchingOne() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        publish(adminCookie, admin.csrfToken(), null, validArticles())
                .andExpect(status().isCreated());

        publish(adminCookie, admin.csrfToken(), 7, validArticles())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courseware_release_conflict"));

        publish(adminCookie, admin.csrfToken(), 1, validArticles())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void publishRejectsArticleWithoutStemMarker() throws Exception {
        Cookie adminCookie = login(csrf(null), "young", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        List<Map<String, Object>> articles = List.of(article(3L, 3L, "缺题干",
                "【题干1】有题干\n\n【@1】\n\n【@2】\n", List.of("1", "2")));

        publish(adminCookie, admin.csrfToken(), null, articles)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("courseware_validation_failed"))
                .andExpect(jsonPath("$.errors[0]")
                        .value(org.hamcrest.Matchers.containsString("第 2 题缺少题干标记")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_release", Long.class)).isZero();
    }

    @Test
    void anonymousReadIsUnauthorizedAndLearnerCannotPublish() throws Exception {
        mockMvc.perform(get("/api/v1/courseware/articles/{teacherId}/{articleId}/content",
                        administratorAccountId, 3))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        Cookie learnerCookie = login(csrf(null), "Alice", "Learner-password-2026");
        publish(learnerCookie, csrf(learnerCookie).csrfToken(), null, validArticles())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    private ResultActions publish(
            Cookie cookie, String csrfToken, Integer expectVersion, List<Map<String, Object>> articles)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", "高中化学");
        body.put("expectVersion", expectVersion);
        body.put("articles", articles);
        return mockMvc.perform(post("/api/v1/admin/courseware/releases")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private List<Map<String, Object>> validArticles() {
        return List.of(
                article(3L, 3L, "3-1 原电池", """
                        # 原电池

                        【题干1】下列说法正确的是（ ）

                        A. 选项一

                        【@1】

                        【题干2】下列说法错误的是（ ）

                        A. 选项二

                        【@2】
                        """, List.of("1", "2")),
                article(9L, 9L, "3-2 电解池", """
                        【题干1】写出电极反应式 ______。

                        【@1】
                        """, List.of("1")));
    }

    private Map<String, Object> article(
            Long articleId, Long legacyArticleId, String title, String markdown, List<String> questionIds) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("articleId", articleId);
        article.put("legacyArticleId", legacyArticleId);
        article.put("title", title);
        article.put("series", "高中化学/理论课讲义");
        article.put("logicalPath", "高中化学/理论课讲义/" + title + ".md");
        article.put("markdown", markdown);
        article.put("answers", questionIds.stream()
                .map(id -> Map.<String, Object>of("questionId", id, "answer", "A", "autoGrade", true))
                .toList());
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
        Cookie cookie = existingCookie == null ? requireCookie(result) : existingCookie;
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
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

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("courseware-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
