package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 学习记录快照：作答与错题写入时固化题干，课件改版后历史记录不变。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-snapshot-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareSnapshotHttpContractTest {

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
        jdbc.update("DELETE FROM public.wrong_questions");
        jdbc.update("DELETE FROM public.article_question_submissions");
        jdbc.update("DELETE FROM public.article_answer_keys");
        jdbc.update("DELETE FROM public.course_notification");
        jdbc.update("DELETE FROM public.course_copy");
        jdbc.update("DELETE FROM public.course_draft");
        jdbc.update("DELETE FROM public.course_share");
        jdbc.update("DELETE FROM public.course_article");
        jdbc.update("DELETE FROM public.course_release_head");
        jdbc.update("DELETE FROM public.course_release");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}')");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{9}')");
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (2, 'young', 'young', ?, 'active', 'administrator', now())
                """, passwords.encode("Admin-password-2026"));
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (5, 'Alice', 'alice', ?, 'active', 'learner', now())
                """, passwords.encode("Learner-password-2026"));
        jdbc.update("""
                INSERT INTO public.article_answer_keys (blog_id, question_id, answer_text, auto_grade)
                VALUES (9, '1', 'A', true)
                """);
    }

    @Test
    void wrongBookKeepsStemSnapshotAfterCoursewareIsRepublished() throws Exception {
        Cookie admin = login(csrf(null), "young", "Admin-password-2026");
        String adminCsrf = csrf(admin).csrfToken();
        publish(admin, adminCsrf, "【题干1】原电池的工作原理是\n\nA. 选项一\n\nB. 选项二\n\n【@1】\n")
                .andExpect(status().isCreated());

        Cookie learner = login(csrf(null), "Alice", "Learner-password-2026");
        mockMvc.perform(put("/api/v1/articles/{articleId}/submissions/{questionId}", 9, "1")
                        .cookie(learner)
                        .header("X-CSRF-TOKEN", csrf(learner).csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerText\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewResult").value("wrong"));

        mockMvc.perform(get("/api/v1/wrong-book").cookie(learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemSnapshot")
                        .value(org.hamcrest.Matchers.containsString("原电池的工作原理是")))
                .andExpect(jsonPath("$[0].subject").value("高中化学"));

        // 发布新版后，历史错题的题干仍是当时那份
        publish(admin, adminCsrf, "【题干1】完全换掉的题干\n\n【@1】\n").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wrong-book").cookie(learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemSnapshot")
                        .value(org.hamcrest.Matchers.containsString("原电池的工作原理是")));

        String stored = jdbc.queryForObject(
                "SELECT stem_snapshot FROM public.wrong_questions WHERE student_id = 5", String.class);
        assertThat(stored).contains("原电池的工作原理是");
        assertThat(jdbc.queryForObject(
                "SELECT subject FROM public.wrong_questions WHERE student_id = 5", String.class))
                .isEqualTo("高中化学");
    }

    private org.springframework.test.web.servlet.ResultActions publish(
            Cookie cookie, String csrfToken, String markdown) throws Exception {
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
            return Files.createTempDirectory("courseware-snapshot-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
