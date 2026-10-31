package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.tutorbase.shared.http.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * P2 编辑器：布局骨架、草稿保存与从草稿发布、导出 zip 与导入。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-editor-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareEditorHttpContractTest {

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
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'alpha', '{}')");
        jdbc.update("""
                INSERT INTO public.account
                    (learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (2, 'alpha', 'alpha', ?, 'active', 'administrator', now())
                """, passwords.encode("Alpha-password-2026"));
    }

    @Test
    void layoutRegistryAndSkeleton() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");

        mockMvc.perform(get("/api/v1/admin/courseware/layouts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].layoutId").value("A"))
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(get("/api/v1/admin/courseware/layouts/B/skeleton")
                        .param("title", "练习3")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("【@1】")));

        mockMvc.perform(get("/api/v1/admin/courseware/layouts/Z/skeleton").cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftsStayPrivateUntilPublished() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/B/skeleton").param("title", "新练习").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asText();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(true))
                .andReturn();
        long articleId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("articleId").asLong();
        assertThat(articleId).isGreaterThanOrEqualTo(1000L);

        mockMvc.perform(get("/api/v1/admin/courseware/drafts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].articleId").value(articleId))
                .andExpect(jsonPath("$[0].published").value(false));

        // 草稿还没发布：学员侧什么都看不到
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_article", Long.class)).isZero();

        String edited = skeleton.replace("在这里写题干", "他 ___ (go) to school every day.");
        mockMvc.perform(put("/api/v1/admin/courseware/drafts/{articleId}", articleId)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", edited))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/precheck")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", edited))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", "高中英语"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.articleCount").value(1));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_article", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT subject FROM public.course_release_head", String.class)).isEqualTo("高中英语");
    }

    @Test
    void publishRejectsUnregisteredLayoutAndTemplateResidue() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "markdown", "---\ntitle: x\n---\n\n<!-- @layout:Z -->\n# 节\n\n正文\n"))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("articleId").asLong()).isPositive();

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", "高中英语"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("courseware_validation_failed"))
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("布局 Z")));

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/precheck")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "markdown", "---\ntitle: {{title}}\n---\n\n<!-- @layout:A -->\n# 节\n\n正文\n"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("模板变量")));
    }

    @Test
    void exportThenImportProducesIndependentDrafts() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "articles", List.of(Map.of(
                                        "articleId", 3,
                                        "legacyArticleId", 3,
                                        "title", "3-1 原电池",
                                        "series", "高中化学",
                                        "logicalPath", "高中化学/理论课讲义/3-1 原电池.md",
                                        "markdown", "【题干1】题\n\n【@1】\n",
                                        "answers", List.of(Map.of(
                                                "questionId", "1", "answer", "A", "autoGrade", true))))))))
                .andExpect(status().isCreated());

        MvcResult exported = mockMvc.perform(get("/api/v1/admin/courseware/export")
                        .param("subject", "高中化学")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        byte[] zip = exported.getResponse().getContentAsByteArray();
        assertThat(zip.length).isGreaterThan(0);
        assertThat(entryNames(zip)).contains(
                "articles/高中化学/理论课讲义/3-1 原电池.md", "manifest.json", "answers.json");

        jdbc.update("DELETE FROM public.course_draft");

        // 导出包里已经有这篇已发布课件，重复导入同路径默认跳过
        mockMvc.perform(multipart("/api/v1/admin/courseware/import")
                        .file(new MockMultipartFile("file", "courseware.zip", "application/zip", zip))
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1));

        mockMvc.perform(multipart("/api/v1/admin/courseware/import")
                        .param("overwrite", "true")
                        .file(new MockMultipartFile("file", "courseware.zip", "application/zip", zip))
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overwritten").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_draft", Long.class)).isEqualTo(1L);

        // 全新路径的 zip 走"导入"分支，分配新的 article_id
        byte[] fresh = zipOf(Map.of("articles/高中化学/新讲义.md", "【题干1】新题\n\n【@1】\n"));
        mockMvc.perform(multipart("/api/v1/admin/courseware/import")
                        .file(new MockMultipartFile("file", "fresh.zip", "application/zip", fresh))
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.items[0].status").value("imported"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.course_draft", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT article_id FROM public.course_draft ORDER BY article_id DESC LIMIT 1", Long.class))
                .isGreaterThanOrEqualTo(1000L);
    }

    @Test
    void importRejectsUnsafeEntries() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        byte[] evil = zipOf(Map.of("articles/../../etc/passwd.md", "x"));

        mockMvc.perform(multipart("/api/v1/admin/courseware/import")
                        .file(new MockMultipartFile("file", "evil.zip", "application/zip", evil))
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf(cookie).csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("courseware_validation_failed"));
    }

    private static byte[] zipOf(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream stream = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                stream.putNextEntry(new ZipEntry(entry.getKey()));
                stream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                stream.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static List<String> entryNames(byte[] zip) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
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

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("courseware-editor-contract-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
