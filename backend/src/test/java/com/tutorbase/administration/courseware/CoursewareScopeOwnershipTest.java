package com.tutorbase.administration.courseware;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 管理员可见范围：发布归属（{@code course_release_head} + {@code course_article.legacy_article_id}）
 * 与旧认领模型共同生效。
 *
 * <p>归属判定必须限定当前生效版本，否则会被同一老师的历史版本行干扰：历史版本里还有某篇、
 * 但当前版本已删除时，该篇不应继续可见。
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "tutor.identity.cookie-name=TUTOR_SESSION",
        "tutor.identity.cookie-secure=false",
        "tutor.identity.csrf-secret=courseware-scope-ownership-secret",
        "tutor.identity.public-mutation-limit=100"
})
class CoursewareScopeOwnershipTest {

    private static final long TEACHER_ONE = 2L;
    private static final long TEACHER_TWO = 3L;
    private static final String CHEMISTRY = "高中化学";

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
    private CoursewareScope scope;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM public.course_release_head");
        jdbc.update("DELETE FROM public.course_article");
        jdbc.update("DELETE FROM public.course_release");
        jdbc.update("DELETE FROM public.admin_courseware_claim");
        jdbc.update("DELETE FROM public.admin_subject_claim");
        jdbc.update("DELETE FROM public.admin_courseware_exclusion");
        jdbc.update("DELETE FROM public.admin_courseware_abandonment");
        jdbc.update("DELETE FROM public.courseware_catalog");
        jdbc.update("DELETE FROM public.account_session");
        jdbc.update("DELETE FROM public.account");
        jdbc.update("DELETE FROM public.student");
        jdbc.update("INSERT INTO public.student (id, username, permissions) VALUES (2, 't1', '{}'), (3, 't2', '{}')");
        jdbc.update("""
                INSERT INTO public.account
                    (id, username, username_normalized, password_hash, status, role, activated_at)
                VALUES (2, 't1', 't1', 'x', 'active', 'administrator', now()),
                       (3, 't2', 't2', 'x', 'active', 'administrator', now())
                """);
    }

    @Test
    void ownsWhatItPublishedWithoutAnyClaimRow() {
        publish(TEACHER_ONE, 1, 3L);

        assertThat(scope.isVisible(TEACHER_ONE, 3L)).isTrue();
        assertThat(scope.isVisible(TEACHER_ONE, 9L)).isFalse();
        assertThat(scope.visibleArticleIds(TEACHER_ONE)).containsExactly(3L);
    }

    @Test
    void doesNotSeeAnotherTeachersArticles() {
        publish(TEACHER_TWO, 1, 3L);

        assertThat(scope.isVisible(TEACHER_ONE, 3L)).isFalse();
        assertThat(scope.isVisible(TEACHER_TWO, 3L)).isTrue();
    }

    @Test
    void historicalVersionDoesNotKeepArticleVisible() {
        publish(TEACHER_ONE, 1, 3L);
        long secondRelease = release(TEACHER_ONE, 2);
        jdbc.update("UPDATE public.course_release_head SET release_id = ? WHERE teacher_account_id = ?",
                secondRelease, TEACHER_ONE);

        assertThat(scope.isVisible(TEACHER_ONE, 3L)).isFalse();
        assertThat(scope.visibleArticleIds(TEACHER_ONE)).isEmpty();
    }

    @Test
    void legacySubjectClaimStillGrantsVisibility() {
        jdbc.update("INSERT INTO public.courseware_catalog (article_id, subject, teacher_account_id) "
                + "VALUES (5, ?, ?)", CHEMISTRY, TEACHER_TWO);
        jdbc.update("INSERT INTO public.admin_subject_claim (admin_account_id, subject) VALUES (?, ?)",
                TEACHER_ONE, CHEMISTRY);

        assertThat(scope.isVisible(TEACHER_ONE, 5L)).isTrue();

        jdbc.update("INSERT INTO public.admin_courseware_exclusion (admin_account_id, article_id) VALUES (?, 5)",
                TEACHER_ONE);

        assertThat(scope.isVisible(TEACHER_ONE, 5L)).isFalse();
    }

    private void publish(long teacherId, int version, long legacyArticleId) {
        long releaseId = release(teacherId, version);
        jdbc.update("""
                INSERT INTO public.course_article
                    (release_id, teacher_account_id, article_id, legacy_article_id, subject, title, series,
                     logical_path, content_hash, answer_hash, question_count)
                VALUES (?, ?, ?, ?, ?, '标题', '课后练习', '高中化学/课后练习/练习-1', 'hash', 'answer', 3)
                """, releaseId, teacherId, legacyArticleId, legacyArticleId, CHEMISTRY);
        jdbc.update("INSERT INTO public.course_release_head (teacher_account_id, subject, release_id) "
                + "VALUES (?, ?, ?)", teacherId, CHEMISTRY, releaseId);
    }

    private long release(long teacherId, int version) {
        return jdbc.queryForObject("""
                INSERT INTO public.course_release
                    (teacher_account_id, subject, version, manifest_path, manifest_hash)
                VALUES (?, ?, ?, 'manifest.json', 'manifest-hash')
                RETURNING id
                """, Long.class, teacherId, CHEMISTRY, version);
    }
}
