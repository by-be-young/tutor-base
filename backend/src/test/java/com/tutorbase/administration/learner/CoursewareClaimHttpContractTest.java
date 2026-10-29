package com.tutorbase.administration.learner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-claim-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareClaimHttpContractTest {

    private static final long CHEMISTRY_ARTICLE_ONE = 101L;
    private static final long CHEMISTRY_ARTICLE_TWO = 102L;
    private static final long ENGLISH_ARTICLE = 201L;
    private static final String CHEMISTRY = "化学";
    private static final String ENGLISH = "英语";

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
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("UPDATE public.student SET claimed_by = NULL, claimed_at = NULL");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (?, ?, ?::integer[])",
                2L, "alice", "{101,201}");
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject, title) VALUES (?, ?, ?)",
                CHEMISTRY_ARTICLE_ONE, CHEMISTRY, "原子结构");
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject, title) VALUES (?, ?, ?)",
                CHEMISTRY_ARTICLE_TWO, CHEMISTRY, "元素周期表");
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject, title) VALUES (?, ?, ?)",
                ENGLISH_ARTICLE, ENGLISH, "时态");
        insertActiveAdministrator(500L, "admin", "Admin-password-2026");
        insertActiveAdministrator(501L, "admin2", "Admin2-password-2026");
        jdbc.update("""
                INSERT INTO public.account
                    (id, learner_id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (?, ?, ?, lower(?), ?, 'active', 'learner', now())
                """, 1001L, 2L, "alice", "alice", passwords.encode("Learner-password-2026"));
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
    void givenCatalogEntriesWhenSyncingThenUpsertsIdempotentlyAndRejectsInvalidPayloads() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        syncCatalog(adminCookie, admin, """
                {"items":[
                    {"articleId":900,"subject":"物理","title":"力学"},
                    {"articleId":901,"subject":"物理","title":"光学"}
                ]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(2));

        syncCatalog(adminCookie, admin, """
                {"items":[
                    {"articleId":900,"subject":"物理","title":"力学（修订）"},
                    {"articleId":901,"subject":"物理","title":"光学"}
                ]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.courseware_catalog WHERE subject = '物理'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM public.courseware_catalog WHERE article_id = 900", String.class))
                .isEqualTo("力学（修订）");

        syncCatalog(adminCookie, admin, "{\"items\":[{\"articleId\":0,\"subject\":\"物理\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        syncCatalog(adminCookie, admin, "{\"items\":[{\"articleId\":902,\"subject\":\"\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        syncCatalog(adminCookie, admin,
                "{\"items\":[{\"articleId\":902,\"subject\":\"物理\",\"title\":\""
                        + "a".repeat(301) + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        syncCatalog(adminCookie, admin, "{\"items\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void givenAdministratorWhenClaimingSubjectThenClaimsAndMineCatalogReflectItAndRepeatIsIdempotent()
            throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        MvcResult firstClaim = mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.subject").value(CHEMISTRY))
                .andExpect(jsonPath("$.claimedAt").isString())
                .andReturn();
        String firstClaimedAt = readTree(firstClaim).get("claimedAt").asText();

        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedAt").value(firstClaimedAt));

        mockMvc.perform(get("/api/v1/admin/courseware-claims").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectClaims.length()").value(1))
                .andExpect(jsonPath("$.subjectClaims[0]").value(CHEMISTRY))
                .andExpect(jsonPath("$.articleClaims").isEmpty());

        MvcResult mine = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "mine")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].articleId").value(CHEMISTRY_ARTICLE_ONE))
                .andExpect(jsonPath("$.items[0].coveredBySubjectClaim").value(true))
                .andExpect(jsonPath("$.items[0].individuallyClaimed").value(false))
                .andExpect(jsonPath("$.items[1].articleId").value(CHEMISTRY_ARTICLE_TWO))
                .andReturn();
        assertThat(articleIdsIn(readTree(mine)))
                .containsExactly(CHEMISTRY_ARTICLE_ONE, CHEMISTRY_ARTICLE_TWO);

        mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "mine")
                        .cookie(adminCookie))
                .andExpect(jsonPath("$.items[0].claimedBy[0].accountId").value(500))
                .andExpect(jsonPath("$.items[0].claimedBy[0].username").value("admin"));
    }

    @Test
    void givenSubjectClaimWhenReleasedThenOnlyIndividuallyClaimedArticlesStayClaimed() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk());
        mockMvc.perform(put(articleClaimPath(CHEMISTRY_ARTICLE_ONE))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(CHEMISTRY_ARTICLE_ONE))
                .andExpect(jsonPath("$.claimedAt").isString());

        mockMvc.perform(delete(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());

        MvcResult unclaimed = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "unclaimed")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        assertThat(articleIdsIn(readTree(unclaimed)))
                .containsExactlyInAnyOrder(CHEMISTRY_ARTICLE_TWO, ENGLISH_ARTICLE);

        MvcResult mine = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "mine")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        assertThat(articleIdsIn(readTree(mine))).containsExactly(CHEMISTRY_ARTICLE_ONE);

        mockMvc.perform(get("/api/v1/admin/courseware-claims").cookie(adminCookie))
                .andExpect(jsonPath("$.subjectClaims").isEmpty())
                .andExpect(jsonPath("$.articleClaims[0]").value(CHEMISTRY_ARTICLE_ONE));
    }

    @Test
    void givenExplicitArticleClaimWhenRepeatedOrReleasedThenItIsIdempotent() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);

        MvcResult firstClaim = mockMvc.perform(put(articleClaimPath(ENGLISH_ARTICLE))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(ENGLISH_ARTICLE))
                .andReturn();
        String firstClaimedAt = readTree(firstClaim).get("claimedAt").asText();
        mockMvc.perform(put(articleClaimPath(ENGLISH_ARTICLE))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedAt").value(firstClaimedAt));

        mockMvc.perform(delete(articleClaimPath(ENGLISH_ARTICLE))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(articleClaimPath(ENGLISH_ARTICLE))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());

        MvcResult unclaimed = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "unclaimed")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        assertThat(articleIdsIn(readTree(unclaimed))).contains(
                CHEMISTRY_ARTICLE_ONE, CHEMISTRY_ARTICLE_TWO, ENGLISH_ARTICLE);
    }

    @Test
    void givenTwoAdministratorsWhenBothClaimSubjectThenCatalogAllReportsBothClaimers() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        Cookie adminTwoCookie = login("admin2", "Admin2-password-2026");
        BrowserSession adminTwo = csrf(adminTwoCookie);

        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk());
        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminTwoCookie)
                        .header("X-CSRF-TOKEN", adminTwo.csrfToken()))
                .andExpect(status().isOk());

        MvcResult allCatalog = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "all")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();
        tools.jackson.databind.JsonNode catalog = readTree(allCatalog);
        tools.jackson.databind.JsonNode chemistryItem = findItem(catalog, CHEMISTRY_ARTICLE_ONE);
        assertThat(chemistryItem.get("claimedBy").size()).isEqualTo(2);
        assertThat(chemistryItem.get("claimedBy").get(0).get("accountId").asLong()).isEqualTo(500L);
        assertThat(chemistryItem.get("claimedBy").get(0).get("username").asText()).isEqualTo("admin");
        assertThat(chemistryItem.get("claimedBy").get(1).get("accountId").asLong()).isEqualTo(501L);
        assertThat(chemistryItem.get("claimedBy").get(1).get("username").asText()).isEqualTo("admin2");
        assertThat(chemistryItem.get("coveredBySubjectClaim").asBoolean()).isTrue();

        tools.jackson.databind.JsonNode chemistrySubject = findSubject(catalog, CHEMISTRY);
        assertThat(chemistrySubject.get("mine").asBoolean()).isTrue();
        assertThat(chemistrySubject.get("total").asLong()).isEqualTo(2);
        List<String> claimedByOthers = new ArrayList<>();
        chemistrySubject.get("claimedByOthers").forEach(node -> claimedByOthers.add(node.asText()));
        assertThat(claimedByOthers).containsExactly("admin2");

        mockMvc.perform(get("/api/v1/admin/courseware-claims").cookie(adminTwoCookie))
                .andExpect(jsonPath("$.subjectClaims[0]").value(CHEMISTRY));
    }

    @Test
    void givenSubjectClaimWhenNewCoursewareIsSyncedThenItIsImmediatelyCovered() throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk());

        syncCatalog(adminCookie, admin,
                "{\"items\":[{\"articleId\":103,\"subject\":\"化学\",\"title\":\"化学键\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(1));

        MvcResult mine = mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "mine")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        assertThat(articleIdsIn(readTree(mine))).containsExactly(101L, 102L, 103L);

        mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "unclaimed")
                        .cookie(adminCookie))
                .andExpect(jsonPath("$.items[*].articleId", hasItems(201)));
    }

    @Test
    void givenUnauthenticatedLearnerOrInvalidInputWhenCallingClaimEndpointsThenStableProblems()
            throws Exception {
        mockMvc.perform(get("/api/v1/admin/courseware-claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        Cookie learnerCookie = login("alice", "Learner-password-2026");
        BrowserSession learner = csrf(learnerCookie);
        mockMvc.perform(get("/api/v1/admin/courseware-claims").cookie(learnerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        mockMvc.perform(put(articleClaimPath(ENGLISH_ARTICLE))
                        .cookie(learnerCookie)
                        .header("X-CSRF-TOKEN", learner.csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put(articleClaimPath(9_999L))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
        mockMvc.perform(put(subjectClaimPath("物理"))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
        mockMvc.perform(put(articleClaimPath(0L))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(get("/api/v1/admin/courseware-claims/catalog")
                        .param("scope", "owned")
                        .cookie(adminCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void givenArticleNotClaimedByAdministratorWhenReadingOrMutatingSensitiveDataThenForbidden()
            throws Exception {
        jdbc.update("INSERT INTO public.article_answer_keys (blog_id, question_id, answer_text) "
                + "VALUES (101, 'q1', 'answer-101'), (201, 'q1', 'answer-201')");
        jdbc.update("INSERT INTO public.article_question_submissions "
                + "(blog_id, student_id, question_id, answer_text) VALUES (101, 2, 'q1', 'draft')");
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk());

        Cookie adminTwoCookie = login("admin2", "Admin2-password-2026");
        BrowserSession adminTwo = csrf(adminTwoCookie);

        mockMvc.perform(get("/api/v1/admin/articles/answer-key-article-ids").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(101));
        mockMvc.perform(get("/api/v1/admin/articles/answer-key-article-ids").cookie(adminTwoCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/articles/101/answer-keys").cookie(adminTwoCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
        mockMvc.perform(put("/api/v1/admin/articles/101/answer-keys")
                        .cookie(adminTwoCookie)
                        .header("X-CSRF-TOKEN", adminTwo.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"questionId\":\"q1\",\"answerText\":\"x\",\"autoGrade\":false}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
        mockMvc.perform(get("/api/v1/admin/articles/101/study-state")
                        .param("learnerId", "2")
                        .cookie(adminTwoCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
        mockMvc.perform(put("/api/v1/admin/articles/101/learners/2/submissions/q1/review")
                        .cookie(adminTwoCookie)
                        .header("X-CSRF-TOKEN", adminTwo.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"correct\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
        mockMvc.perform(get("/api/v1/admin/learners/2/study-progress").cookie(adminTwoCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/v1/admin/learners/2/content-grants")
                        .cookie(adminTwoCookie)
                        .header("X-CSRF-TOKEN", adminTwo.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[201]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));

        mockMvc.perform(get("/api/v1/admin/learners/2/study-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].articleId").value(101));
        mockMvc.perform(get("/api/v1/admin/articles/101/answer-keys").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answerText").value("answer-101"));
        mockMvc.perform(get("/api/v1/admin/articles/201/answer-keys").cookie(adminCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
    }

    @Test
    void givenLearnerGrantsOutsideAdministratorScopeWhenReplacingThenForeignGrantsArePreserved()
            throws Exception {
        Cookie adminCookie = login("admin", "Admin-password-2026");
        BrowserSession admin = csrf(adminCookie);
        mockMvc.perform(put(subjectClaimPath(CHEMISTRY))
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk());

        // 学员原有授权：101（化学，当前管理员可见）与 201（英语，当前管理员不可见）。
        mockMvc.perform(put("/api/v1/admin/learners/2/content-grants")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[102]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentGrantArticleIds.length()").value(2))
                .andExpect(jsonPath("$.contentGrantArticleIds[0]").value(102))
                .andExpect(jsonPath("$.contentGrantArticleIds[1]").value(201));
        assertThat(jdbc.queryForObject(
                "SELECT permissions::text FROM public.student WHERE id = 2", String.class))
                .isEqualTo("{102,201}");

        mockMvc.perform(put("/api/v1/admin/learners/2/content-grants")
                        .cookie(adminCookie)
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[201]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("courseware_not_claimed"));
        assertThat(jdbc.queryForObject(
                "SELECT permissions::text FROM public.student WHERE id = 2", String.class))
                .isEqualTo("{102,201}");
    }

    private org.springframework.test.web.servlet.ResultActions syncCatalog(
            Cookie cookie, BrowserSession session, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/courseware-catalog/sync")
                .cookie(cookie)
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
    private java.net.URI subjectClaimPath(String subject) {
        return java.net.URI.create(
                "/api/v1/admin/courseware-claims/subjects/" + URLEncoder.encode(subject, StandardCharsets.UTF_8));
    }

    private String articleClaimPath(long articleId) {
        return "/api/v1/admin/courseware-claims/articles/" + articleId;
    }

    private tools.jackson.databind.JsonNode findItem(tools.jackson.databind.JsonNode catalog, long articleId) {
        for (tools.jackson.databind.JsonNode item : catalog.get("items")) {
            if (item.get("articleId").asLong() == articleId) {
                return item;
            }
        }
        throw new AssertionError("catalog response does not contain article " + articleId);
    }

    private tools.jackson.databind.JsonNode findSubject(
            tools.jackson.databind.JsonNode catalog, String subject) {
        for (tools.jackson.databind.JsonNode item : catalog.get("subjects")) {
            if (subject.equals(item.get("subject").asText())) {
                return item;
            }
        }
        throw new AssertionError("catalog response does not contain subject " + subject);
    }

    private List<Long> articleIdsIn(tools.jackson.databind.JsonNode tree) {
        List<Long> articleIds = new ArrayList<>();
        tree.get("items").forEach(item -> articleIds.add(item.get("articleId").asLong()));
        return articleIds;
    }

    private tools.jackson.databind.JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
