package com.tutorbase.courseware.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
        clearStorage();
        jdbc.update("DELETE FROM public.course_copy");
        jdbc.update("DELETE FROM public.course_draft");
        jdbc.update("DELETE FROM public.course_share");
        jdbc.update("DELETE FROM public.course_article");
        jdbc.update("DELETE FROM public.course_release_head");
        jdbc.update("DELETE FROM public.course_release");
        jdbc.update("DELETE FROM public.course_operation_log");
        jdbc.update("DELETE FROM public.course_notification");
        jdbc.update("DELETE FROM public.courseware_catalog");
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
                .andReturn().getResponse().getContentAsString()).get("markdown").asString();

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
    void articlesListKeepsPublishedCoursewareAfterDraftRemoved() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/B/skeleton").param("title", "口语练习").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asString();

        long articleId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andReturn().getResponse().getContentAsString()).get("articleId").asLong();

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", "高中英语"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(1));

        // 发布成功后草稿自动清空：drafts 列表空，已发布课件仍必须可选
        mockMvc.perform(get("/api/v1/admin/courseware/drafts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/courseware/articles").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].articleId").value(articleId))
                .andExpect(jsonPath("$[0].published").value(true))
                .andExpect(jsonPath("$[0].hasDraft").value(false))
                .andExpect(jsonPath("$[0].subject").value("高中英语"))
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].logicalPath").isNotEmpty());
    }

    @Test
    void publishedArticleWithMissingBlobReportsContentMissing() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/B/skeleton").param("title", "口语练习").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asString();

        long articleId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andReturn().getResponse().getContentAsString()).get("articleId").asLong();

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", "高中英语"))))
                .andExpect(status().isOk());

        long alphaId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        String hash = jdbc.queryForObject("""
                SELECT content_hash FROM public.course_article
                WHERE teacher_account_id = ? AND article_id = ?
                """, String.class, alphaId, articleId);

        // 发布后草稿会被清掉，读取直接回落到已发布正文
        mockMvc.perform(get("/api/v1/admin/courseware/drafts/{articleId}", articleId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(false));

        // 模拟存储卷重建：正文文件不在本机。清单要标出来，读取要给出可区分的 409。
        Files.delete(STORAGE_ROOT.resolve(Long.toString(alphaId))
                .resolve("blobs")
                .resolve(hash.substring("sha256:".length()) + ".md"));

        mockMvc.perform(get("/api/v1/admin/courseware/articles").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.articleId == " + articleId + ")].contentMissing", contains(true)));

        mockMvc.perform(get("/api/v1/admin/courseware/drafts/{articleId}", articleId).cookie(cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courseware_content_missing"));
    }

    @Test
    void draftListHasOneRowPerDraftEvenWithMultipleSubjects() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();
        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/A/skeleton").param("title", "多科目").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asString();

        // 先造两个科目的发布版本（course_release_head 每个科目一行）
        for (String subject : List.of("高中英语", "高中化学")) {
            String markdown = skeleton.replace("# 新课件", "# " + subject + " 新课件");
            mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                            .cookie(cookie)
                            .header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("markdown", markdown))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                            .cookie(cookie)
                            .header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("subject", subject))))
                    .andExpect(status().isOk());
        }
        // 发布后草稿应被清空
        mockMvc.perform(get("/api/v1/admin/courseware/drafts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        long articleId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andReturn().getResponse().getContentAsString()).get("articleId").asLong();

        // 两个科目各有一份 release head，草稿列表也不能因此把同一条草稿列两遍
        mockMvc.perform(get("/api/v1/admin/courseware/drafts").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].articleId").value(articleId));
    }

    @Test
    void catalogOnlyStaticCoursewareAppearsAfterDraftAndPublishesBackToItsOwnArticle() throws Exception {
        long alphaId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        long legacyId = 901L;
        String subject = "高中日语";
        jdbc.update("DELETE FROM public.course_draft WHERE teacher_account_id = ? AND article_id = ?",
                alphaId, legacyId);
        // 口语训练-1 这类旧静态课件：只在 courseware_catalog 里有行，从未发布过
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject, title) VALUES (?, ?, ?)",
                legacyId, subject, "口语训练-1");
        jdbc.update("INSERT INTO public.admin_subject_claim (admin_account_id, subject) VALUES (?, ?)",
                alphaId, subject);

        try {
            Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
            String csrf = csrf(cookie).csrfToken();

            // 只有 catalog 行、正文还没托管时不再返回给浏览器，避免读取 public/articles
            mockMvc.perform(get("/api/v1/admin/courseware/articles").cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.articleId == 901)]", org.hamcrest.Matchers.empty()));

            String skeleton = objectMapper.readTree(mockMvc.perform(
                            get("/api/v1/admin/courseware/layouts/A/skeleton")
                                    .param("title", "口语训练-1").cookie(cookie))
                    .andReturn().getResponse().getContentAsString()).get("markdown").asString();

            mockMvc.perform(put("/api/v1/admin/courseware/drafts/{articleId}", legacyId)
                            .cookie(cookie)
                            .header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasDraft").value(true));

            // 草稿正文已经进数据库，可以作为未发布篇目出现在清单里
            mockMvc.perform(get("/api/v1/admin/courseware/articles").cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.articleId == 901)].title", contains("口语训练-1")))
                    .andExpect(jsonPath("$[?(@.articleId == 901)].published", contains(false)))
                    .andExpect(jsonPath("$[?(@.articleId == 901)].hosted", contains(true)))
                    .andExpect(jsonPath("$[?(@.articleId == 901)].hasDraft", contains(true)));

            mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                            .cookie(cookie)
                            .header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("subject", subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articleCount").value(1));

            // 发布后仍挂回 legacy 篇目：学员端看到的是原篇目的新版本，而不是凭空多出一篇
            Long publishedLegacyId = jdbc.queryForObject("""
                    SELECT ca.legacy_article_id
                    FROM public.course_article AS ca
                    JOIN public.course_release_head AS h
                      ON h.release_id = ca.release_id AND h.teacher_account_id = ca.teacher_account_id
                    WHERE ca.teacher_account_id = ? AND ca.article_id = ?
                    """, Long.class, alphaId, legacyId);
            assertThat(publishedLegacyId).isEqualTo(legacyId);
        } finally {
            jdbc.update("DELETE FROM public.course_release WHERE teacher_account_id = ? AND subject = ?",
                    alphaId, subject);
            jdbc.update("DELETE FROM public.course_draft WHERE teacher_account_id = ? AND article_id = ?",
                    alphaId, legacyId);
            jdbc.update("DELETE FROM public.admin_subject_claim WHERE admin_account_id = ? AND subject = ?",
                    alphaId, subject);
            jdbc.update("DELETE FROM public.courseware_catalog WHERE article_id = ?", legacyId);
        }
    }

    /**
     * 已发布篇目的正文 blob 丢失（换机、容器重建、课件卷未挂载）时，发布必须按上一版哈希结转，
     * 既不能 500，也不能把整个科目的课件清空。
     */
    @Test
    void publishCarriesOverArticlesWhoseBlobsAreLost() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();
        String subject = "高中化学";

        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", subject,
                                "articles", List.of(
                                        Map.of("articleId", 3, "legacyArticleId", 3,
                                                "title", "3-1 原电池", "series", subject,
                                                "logicalPath", subject + "/理论课讲义/3-1 原电池.md",
                                                "markdown", "【题干1】旧\n\n【@1】\n",
                                                "answers", List.of(Map.of(
                                                        "questionId", "1", "answer", "A", "autoGrade", true))),
                                        Map.of("articleId", 9, "legacyArticleId", 9,
                                                "title", "3-2 电解池", "series", subject,
                                                "logicalPath", subject + "/理论课讲义/3-2 电解池.md",
                                                "markdown", "【题干1】电解\n\n【@1】\n",
                                                "answers", List.of(Map.of(
                                                        "questionId", "1", "answer", "B", "autoGrade", true))))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1));

        List<String> hashesBefore = jdbc.queryForList("""
                SELECT ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.subject = ? ORDER BY ca.logical_path
                """, String.class, subject);
        assertThat(hashesBefore).hasSize(2);

        // 模拟正文文件丢失：删掉该老师 blobs 目录下的全部正文
        Path blobs = STORAGE_ROOT.resolve("2").resolve("blobs");
        if (Files.isDirectory(blobs)) {
            try (var paths = Files.list(blobs)) {
                for (Path path : paths.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.articleCount").value(2));

        assertThat(jdbc.queryForList("""
                SELECT ca.content_hash
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.subject = ? ORDER BY ca.logical_path
                """, String.class, subject)).isEqualTo(hashesBefore);
    }

    /**
     * 历史脚本写入的 content_hash 是不带 {@code sha256:} 前缀的裸 hex。存储层按 hex 落盘，
     * 清单与 GC 必须归一化后再比较，否则正文明明在也会整片标成「正文缺失」。
     */
    @Test
    void rawHexContentHashIsStillTreatedAsPresent() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();
        String subject = "高中英语";

        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/A/skeleton")
                                .param("title", "裸哈希课件").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asString();
        long articleId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andReturn().getResponse().getContentAsString()).get("articleId").asLong();

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", subject))))
                .andExpect(status().isOk());

        long alphaId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        jdbc.update("""
                UPDATE public.course_article SET content_hash = replace(content_hash, 'sha256:', '')
                WHERE teacher_account_id = ? AND article_id = ?
                """, alphaId, articleId);

        mockMvc.perform(get("/api/v1/admin/courseware/articles").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.articleId == " + articleId + ")].contentMissing", contains(false)));

        mockMvc.perform(get("/api/v1/admin/courseware/maintenance/usage").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orphanCount").value(0));

        mockMvc.perform(get("/api/v1/admin/courseware/drafts/{articleId}", articleId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("裸哈希课件")));
    }

    /**
     * 在课件页「移动」改名后，发布元数据、正文首标题与旧认领页使用的 courseware_catalog 必须一起更新。
     */
    @Test
    void movingArticleRenamesPublishedTitleBodyHeadingAndCatalog() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();
        String subject = "高中英语";

        String skeleton = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/courseware/layouts/A/skeleton")
                                .param("title", "虚拟语气").cookie(cookie))
                .andReturn().getResponse().getContentAsString()).get("markdown").asString()
                .replace("在这里写正文", "正文");

        long articleId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/courseware/drafts")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", skeleton))))
                .andReturn().getResponse().getContentAsString()).get("articleId").asLong();

        long alphaId = jdbc.queryForObject(
                "SELECT id FROM public.account WHERE username = 'alpha'", Long.class);
        jdbc.update("""
                INSERT INTO public.courseware_catalog
                    (article_id, teacher_account_id, legacy_article_id, subject, title)
                VALUES (?, ?, ?, ?, ?)
                """, articleId, alphaId, articleId, subject, "虚拟语气");

        mockMvc.perform(post("/api/v1/admin/courseware/drafts/publish")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subject", subject))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/courseware/articles/{articleId}/move", articleId)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newLogicalPath", "高中英语/语法课讲义/3 虚拟语气.md"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.newPath").value("高中英语/语法课讲义/3 虚拟语气.md"));

        assertThat(jdbc.queryForObject("""
                SELECT ca.title
                FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.teacher_account_id = ? AND ca.article_id = ?
                """, String.class, alphaId, articleId)).isEqualTo("3 虚拟语气");
        assertThat(jdbc.queryForObject("""
                SELECT title FROM public.courseware_catalog
                WHERE teacher_account_id = ? AND article_id = ?
                """, String.class, alphaId, articleId)).isEqualTo("3 虚拟语气");

        mockMvc.perform(get("/api/v1/admin/courseware/drafts/{articleId}", articleId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(
                        org.hamcrest.Matchers.containsString("# 3 虚拟语气")));
    }

    @Test
    void publishRejectsUnregisteredLayoutAndTemplateResidue() throws Exception {        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
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

    @Test
    void templateLibraryListsSeedsAndSavesCustomOnes() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        mockMvc.perform(get("/api/v1/admin/courseware/templates").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].kind").value("new_lesson"));

        long templateId = jdbc.queryForObject(
                "SELECT id FROM public.course_template WHERE kind = 'practice'", Long.class);
        mockMvc.perform(get("/api/v1/admin/courseware/templates/{id}", templateId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("【@1】")));

        MvcResult saved = mockMvc.perform(post("/api/v1/admin/courseware/templates")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "kind", "topic",
                                "name", "电化学专题",
                                "markdown", "<!-- @layout:A -->\n# 专题\n\n正文\n"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("电化学专题"))
                .andReturn();
        assertThat(objectMapper.readTree(saved.getResponse().getContentAsString()).get("id").asLong()).isPositive();

        // 带科目的模板只在对应科目下可见
        mockMvc.perform(get("/api/v1/admin/courseware/templates")
                        .param("subject", "高中化学")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get("/api/v1/admin/courseware/templates")
                        .param("subject", "高中英语")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(post("/api/v1/admin/courseware/templates")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "unknown", "name", "x", "markdown", "y"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workspacePullEditPushCreatesNewVersion() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "articles", List.of(
                                        Map.of("articleId", 3, "legacyArticleId", 3,
                                                "title", "3-1 原电池", "series", "高中化学",
                                                "logicalPath", "高中化学/理论课讲义/3-1 原电池.md",
                                                "markdown", "【题干1】旧\n\n【@1】\n",
                                                "answers", List.of(Map.of(
                                                        "questionId", "1", "answer", "A", "autoGrade", true))),
                                        Map.of("articleId", 9, "legacyArticleId", 9,
                                                "title", "3-2 电解池", "series", "高中化学",
                                                "logicalPath", "高中化学/理论课讲义/3-2 电解池.md",
                                                "markdown", "【题干1】电解\n\n【@1】\n",
                                                "answers", List.of(Map.of(
                                                        "questionId", "1", "answer", "B", "autoGrade", true))))))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/courseware/workspace/manifest")
                        .param("subject", "高中化学")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].contentHash")
                        .value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.entries[0].markdown").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/courseware/workspace/files")
                        .param("subject", "高中化学")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files['高中化学/理论课讲义/3-1 原电池.md']")
                        .value(org.hamcrest.Matchers.containsString("【题干1】旧")));

        // 改一篇、加一篇、删一篇
        mockMvc.perform(post("/api/v1/admin/courseware/workspace/push")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "baseVersion", 1,
                                "files", List.of(
                                        Map.of("logicalPath", "高中化学/理论课讲义/3-1 原电池.md",
                                                "markdown", "【题干1】新\n\n【@1】\n"),
                                        Map.of("logicalPath", "高中化学/理论课讲义/3-3 新增.md",
                                                "markdown", "【题干1】新增\n\n【@1】\n")),
                                "deletedPaths", List.of("高中化学/理论课讲义/3-2 电解池.md")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.articleCount").value(2))
                .andExpect(jsonPath("$.changedCount").value(2))
                .andExpect(jsonPath("$.deletedCount").value(1));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE h.subject = '高中化学' AND ca.logical_path LIKE '%3-2 电解池%'
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT ca.title FROM public.course_release_head AS h
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id AND ca.teacher_account_id = h.teacher_account_id
                WHERE ca.logical_path LIKE '%3-3 新增%'
                """, String.class)).isEqualTo("3-3 新增");

        // 过期 baseVersion → 409
        mockMvc.perform(post("/api/v1/admin/courseware/workspace/push")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "baseVersion", 1,
                                "files", List.of(Map.of("logicalPath", "高中化学/x.md", "markdown", "x"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courseware_release_conflict"));

        // 未知布局 → 400
        mockMvc.perform(post("/api/v1/admin/courseware/workspace/push")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "baseVersion", 2,
                                "files", List.of(Map.of("logicalPath", "高中化学/x.md",
                                        "markdown", "<!-- @layout:Z -->\n# 节\n\n正文\n"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("布局 Z")));
    }

    @Test
    void overviewSummarizesSubjectsAndPendingWork() throws Exception {
        Cookie cookie = login(csrf(null), "alpha", "Alpha-password-2026");
        String csrf = csrf(cookie).csrfToken();

        mockMvc.perform(post("/api/v1/admin/courseware/releases")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "高中化学",
                                "articles", List.of(Map.of(
                                        "articleId", 3, "legacyArticleId", 3,
                                        "title", "3-1 原电池", "series", "高中化学",
                                        "logicalPath", "高中化学/理论课讲义/3-1 原电池.md",
                                        "markdown", "【题干1】题\n\n【@1】\n",
                                        "answers", List.of(Map.of(
                                                "questionId", "1", "answer", "A", "autoGrade", true))))))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/courseware/overview").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[0].subject").value("高中化学"))
                .andExpect(jsonPath("$.subjects[0].version").value(1))
                .andExpect(jsonPath("$.subjects[0].articleCount").value(1))
                .andExpect(jsonPath("$.subjects[0].draftCount").value(0))
                .andExpect(jsonPath("$.pendingDrafts").value(0))
                .andExpect(jsonPath("$.acquiredCount").value(0))
                .andExpect(jsonPath("$.pendingUpdates").value(0))
                .andExpect(jsonPath("$.unreadNotifications").value(0));

        // 存一份草稿后概览能看到待发布数量
        mockMvc.perform(put("/api/v1/admin/courseware/drafts/{articleId}", 3)
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "markdown", "【题干1】改过的题干\n\n【@1】\n"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/courseware/overview").cookie(cookie))
                .andExpect(jsonPath("$.subjects[0].draftCount").value(1))
                .andExpect(jsonPath("$.pendingDrafts").value(1));
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
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
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

    private static void clearStorage() {
        if (!Files.isDirectory(STORAGE_ROOT)) {
            return;
        }
        try (var paths = Files.walk(STORAGE_ROOT)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(STORAGE_ROOT))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (java.io.IOException ignored) {
                            // 测试隔离失败不应掩盖真正的断言结果
                        }
                    });
        } catch (java.io.IOException ignored) {
            // 同上
        }
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
