package com.tutorbase.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=reward-contract-test-secret",
        "tutor.identity.public-mutation-limit=100"
})
class RewardHttpContractTest {

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
        jdbc.update("DELETE FROM public.achievement_claims");
        jdbc.update("DELETE FROM public.card_collection");
        jdbc.update("DELETE FROM public.task_claims");
        jdbc.update("DELETE FROM public.task_assignments");
        jdbc.update("DELETE FROM public.daily_check_in");
        jdbc.update("DELETE FROM public.user_points");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account_activation");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (5, 'Alice', '{}')");
        insertActiveAccount(5, "Alice", "learner", "Learner-password-2026");
    }

    @Test
    void learnerReadsOwnBalanceCollectionAndStats() throws Exception {
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (6, 'Bob', '{}')");
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points, draw_count, updated_at)
                VALUES (5, 400, 1200, 3, now()), (6, 900, 900, 0, now())
                """);
        jdbc.update("""
                INSERT INTO public.card_collection (student_id, card_key, set_key, rarity)
                VALUES (5, 'puppy-c0', 'puppy', 'common'),
                       (5, 'puppy-c0', 'puppy', 'common'),
                       (5, 'ocean-r', 'ocean', 'rare'),
                       (6, 'private-card', 'sport', 'common')
                """);
        Cookie learner = login("Alice", "Learner-password-2026");

        mockMvc.perform(get("/api/v1/rewards").cookie(learner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.points").value(400))
                .andExpect(jsonPath("$.lifetimePoints").value(1200))
                .andExpect(jsonPath("$.collection.length()").value(3))
                .andExpect(jsonPath("$.collection[0].isUp").doesNotExist())
                .andExpect(jsonPath("$.stats.totalDraws").value(3))
                .andExpect(jsonPath("$.stats.totalCards").value(3))
                .andExpect(jsonPath("$.stats.uniqueCards").value(2))
                .andExpect(jsonPath("$.stats.totalCatalogCards").value(70))
                .andExpect(jsonPath("$.stats.totalSets").value(10));

        // 只有自己的收藏会进入响应。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class))
                .isEqualTo(3);
    }

    @Test
    void catalogIsDataDrivenWithWeeklyUpCard() throws Exception {
        Cookie learner = login("Alice", "Learner-password-2026");

        MvcResult result = mockMvc.perform(get("/api/v1/rewards/catalog").cookie(learner))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.drawCost").value(80))
                .andExpect(jsonPath("$.rareRate").value(0.05))
                .andExpect(jsonPath("$.upRateWhenRare").value(0.5))
                .andExpect(jsonPath("$.currentUp.setKey").isNotEmpty())
                .andExpect(jsonPath("$.currentUp.cardKey").isNotEmpty())
                .andExpect(jsonPath("$.currentUp.name").isNotEmpty())
                .andExpect(jsonPath("$.currentUp.setName").isNotEmpty())
                .andExpect(jsonPath("$.currentUp.image", startsWith("cards/")))
                .andExpect(jsonPath("$.currentUp.rotatesAt").isNotEmpty())
                .andExpect(jsonPath("$.sets.length()").value(10))
                .andExpect(jsonPath("$.sets[0].key").value("puppy"))
                .andExpect(jsonPath("$.sets[0].rare.cardKey").value("puppy-r"))
                .andExpect(jsonPath("$.sets[0].rare.image", startsWith("cards/")))
                .andExpect(jsonPath("$.sets[0].commons.length()").value(6))
                .andExpect(jsonPath("$.sets[0].commons[0].cardKey").value("puppy-c0"))
                .andExpect(jsonPath("$.totalCards").value(70))
                .andExpect(jsonPath("$.totalSets").value(10))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String upCardKey = body.get("currentUp").get("cardKey").asString();
        // 当期 UP 必须是某个启用卡组的稀有卡。
        boolean found = false;
        for (JsonNode set : body.get("sets")) {
            if (upCardKey.equals(set.get("rare").get("cardKey").asString())) {
                assertThat(set.get("key").asString())
                        .isEqualTo(body.get("currentUp").get("setKey").asString());
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void drawConsumesPointsIncrementsDrawCountAndReturnsUpMetadata() throws Exception {
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points, updated_at)
                VALUES (5, 800, 2000, now())
                """);
        Cookie learner = login("Alice", "Learner-password-2026");
        BrowserSession browser = csrf(learner);

        MvcResult result = mockMvc.perform(post("/api/v1/rewards/draws")
                        .cookie(learner)
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("count", 10))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.lifetimePoints").value(2000))
                .andExpect(jsonPath("$.currentUp.cardKey").isNotEmpty())
                .andExpect(jsonPath("$.cards.length()").value(10))
                .andExpect(jsonPath("$.stats.totalDraws").value(10))
                .andExpect(jsonPath("$.stats.totalCards").value(10))
                .andExpect(jsonPath("$.stats.totalCatalogCards").value(70))
                .andExpect(jsonPath("$.stats.totalSets").value(10))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String upCardKey = body.get("currentUp").get("cardKey").asString();
        for (JsonNode card : body.get("cards")) {
            assertThat(card.get("cardKey").asString()).isNotEmpty();
            assertThat(card.get("setKey").asString()).isNotEmpty();
            assertThat(card.get("rarity").asString()).isIn("common", "rare");
            assertThat(card.get("claimedAt").asString()).isNotEmpty();
            assertThat(card.get("isUp").isBoolean()).isTrue();
            boolean expectedUp = "rare".equals(card.get("rarity").asString())
                    && upCardKey.equals(card.get("cardKey").asString());
            assertThat(card.get("isUp").asBoolean()).isEqualTo(expectedUp);
        }
        // 抽卡只扣 points，不消耗 lifetime_points；累计抽卡次数单独计数。
        Map<String, Object> points = jdbc.queryForMap(
                "SELECT points, lifetime_points, draw_count FROM public.user_points WHERE student_id = 5");
        assertThat(((Number) points.get("points")).longValue()).isZero();
        assertThat(((Number) points.get("lifetime_points")).longValue()).isEqualTo(2000);
        assertThat(((Number) points.get("draw_count")).longValue()).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT card_key) FROM public.card_collection WHERE student_id = 5
                """, Long.class)).isBetween(1L, 10L);
    }

    @Test
    void drawRejectsInsufficientPointsWithStableProblemCode() throws Exception {
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points, updated_at)
                VALUES (5, 100, 500, now())
                """);
        Cookie learner = login("Alice", "Learner-password-2026");
        BrowserSession browser = csrf(learner);

        mockMvc.perform(post("/api/v1/rewards/draws")
                        .cookie(learner)
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("count", 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient_points"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT draw_count FROM public.user_points WHERE student_id = 5", Long.class)).isZero();
    }

    @Test
    void drawValidatesCountRange() throws Exception {
        jdbc.update("""
                INSERT INTO public.user_points (student_id, points, lifetime_points, updated_at)
                VALUES (5, 10000, 10000, now())
                """);
        Cookie learner = login("Alice", "Learner-password-2026");
        BrowserSession browser = csrf(learner);

        for (int count : new int[]{0, 11}) {
            mockMvc.perform(post("/api/v1/rewards/draws")
                            .cookie(learner)
                            .header("X-CSRF-TOKEN", browser.csrfToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("count", count))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_failed"));
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.card_collection WHERE student_id = 5", Long.class)).isZero();
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mockMvc.perform(get("/api/v1/rewards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mockMvc.perform(get("/api/v1/rewards/catalog"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mockMvc.perform(post("/api/v1/rewards/draws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("count", 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    private Cookie login(String username, String password) throws Exception {
        BrowserSession browser = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(browser.cookie())
                        .header("X-CSRF-TOKEN", browser.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(result);
    }

    private BrowserSession csrf(Cookie existingCookie) throws Exception {
        var request = get("/api/v1/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        Cookie cookie = existingCookie == null ? requireCookie(result) : existingCookie;
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
        return new BrowserSession(cookie, token);
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

    private record BrowserSession(Cookie cookie, String csrfToken) {
    }
}
