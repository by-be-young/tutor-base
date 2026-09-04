package com.tutorbase.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=learning-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class LearningAndWrongBookHttpContractTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired WebApplicationContext context;
    @Autowired TraceIdFilter traceIdFilter;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    MockMvc mvc;

    @BeforeEach
    void reset() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(traceIdFilter)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc.update("DELETE FROM public.wrong_questions");
        jdbc.update("DELETE FROM public.article_question_submissions");
        jdbc.update("DELETE FROM public.article_answer_keys");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 'young', '{}'), (5, 'Alice', '{10}'), (6, 'Bob', '{10}')");
        account(2, "young", "administrator", "Admin-password-2026");
        account(5, "Alice", "learner", "Learner-password-2026");
        account(6, "Bob", "learner", "Bob-password-2026");
        jdbc.update("INSERT INTO public.article_answer_keys (blog_id, question_id, answer_text, auto_grade) VALUES (10, 'q1', '42', true)");
    }

    @Test
    void answerIsHiddenUntilServerAutoGradesAndWrongAnswerIsCollectedAtomically() throws Exception {
        Cookie learner = login("Alice", "Learner-password-2026");
        mvc.perform(get("/api/v1/articles/10/study-state").cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].answerText").isEmpty());

        mvc.perform(put("/api/v1/articles/10/submissions/q1").cookie(learner).header("X-CSRF-TOKEN", csrf(learner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"41\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("reviewed"))
                .andExpect(jsonPath("$.reviewResult").value("wrong"));

        mvc.perform(get("/api/v1/articles/10/study-state").cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].answerText").value("42"));
        mvc.perform(get("/api/v1/wrong-book").cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].correctAnswer").value("42"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.wrong_questions WHERE student_id=5", Long.class)).isEqualTo(1);
    }

    @Test
    void learnerCannotAccessUngrantedArticleOrResubmitReviewedAnswer() throws Exception {
        Cookie learner = login("Alice", "Learner-password-2026");
        mvc.perform(get("/api/v1/articles/99/study-state").cookie(learner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("content_access_denied"));
        String csrf = csrf(learner);
        mvc.perform(put("/api/v1/articles/10/submissions/q1").cookie(learner).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"42\"}"));
        mvc.perform(put("/api/v1/articles/10/submissions/q1").cookie(learner).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answerText\":\"changed\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("submission_already_reviewed"));
    }

    @Test
    void administratorCanSaveKeysAndReviewButLearnerCannotModifyAnotherWrongEntry() throws Exception {
        jdbc.update("INSERT INTO public.article_question_submissions (blog_id, student_id, question_id, answer_text) VALUES (10, 5, 'manual', 'draft')");
        Cookie admin = login("young", "Admin-password-2026");
        mvc.perform(put("/api/v1/admin/articles/10/answer-keys").cookie(admin).header("X-CSRF-TOKEN", csrf(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"questionId\":\"manual\",\"answerText\":\"reference\",\"autoGrade\":false}]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.article_answer_keys WHERE blog_id=10 AND question_id='q1'",
                Long.class)).isZero();
        mvc.perform(put("/api/v1/admin/articles/10/learners/5/submissions/manual/review")
                        .cookie(admin).header("X-CSRF-TOKEN", csrf(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"partial\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewResult").value("partial"));

        jdbc.update("INSERT INTO public.wrong_questions (student_id, source_blog_id, source_question_id) VALUES (6, 10, 'bob')");
        long bobEntry = jdbc.queryForObject("SELECT id FROM public.wrong_questions WHERE student_id=6", Long.class);
        Cookie alice = login("Alice", "Learner-password-2026");
        mvc.perform(patch("/api/v1/wrong-book/entries/" + bobEntry).cookie(alice).header("X-CSRF-TOKEN", csrf(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"stolen\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("wrong_book_entry_not_found"));
    }

    @Test
    void learnerCanUpdateAndRemoveOwnManualWrongEntry() throws Exception {
        Cookie alice = login("Alice", "Learner-password-2026");
        String csrf = csrf(alice);
        MvcResult created = mvc.perform(post("/api/v1/wrong-book/entries").cookie(alice)
                        .header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceArticleId\":10,\"sourceQuestionId\":\"q1\",\"myAnswer\":\"draft\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctAnswer").value(""))
                .andReturn();
        long entryId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(patch("/api/v1/wrong-book/entries/" + entryId).cookie(alice)
                        .header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongReason\":\"计算错误\",\"tags\":[\"代数\",\"复习\"],\"mastered\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wrongReason").value("计算错误"))
                .andExpect(jsonPath("$.tags[0]").value("代数"))
                .andExpect(jsonPath("$.mastered").value(true));

        mvc.perform(delete("/api/v1/wrong-book/entries/" + entryId).cookie(alice)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.wrong_questions WHERE id=?", Long.class, entryId))
                .isZero();
    }

    private void account(long learnerId, String username, String role, String password) {
        jdbc.update("INSERT INTO public.account (learner_id, username, username_normalized, password_hash, status, role, activated_at) VALUES (?, ?, lower(?), ?, 'active', ?, now())",
                learnerId, username, username, passwords.encode(password), role);
    }

    private Cookie login(String username, String password) throws Exception {
        Browser browser = anonymousBrowser();
        MvcResult result = mvc.perform(post("/api/v1/sessions").cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return requireCookie(result);
    }

    private Browser anonymousBrowser() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
        return new Browser(requireCookie(result), json.readTree(result.getResponse().getContentAsString()).get("token").asString());
    }

    private String csrf(Cookie cookie) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/csrf").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private Cookie requireCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("TUTOR_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    record Browser(Cookie cookie, String csrf) {}
}
