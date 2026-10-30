package com.tutorbase.courseware.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课件消息提醒：有人获取了我的课件、我获取的来源有更新、来源撤回。
 *
 * <p>只存"给谁的什么提醒"，读状态用 {@code read_at}；跨老师的可见性由业务侧保证。
 */
@Service
public class CoursewareNotificationService {

    private final JdbcClient jdbc;

    CoursewareNotificationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void notifyAccount(long recipientAccountId, String kind, String title, String body,
            Long sourceTeacherAccountId, Long copyId, Long articleId) {
        jdbc.sql("""
                INSERT INTO public.course_notification
                    (recipient_account_id, kind, title, body, source_teacher_account_id, copy_id, article_id)
                VALUES (:recipient, :kind, :title, :body, :sourceTeacher, :copyId, :articleId)
                """)
                .param("recipient", recipientAccountId)
                .param("kind", kind)
                .param("title", title)
                .param("body", body)
                .param("sourceTeacher", sourceTeacherAccountId)
                .param("copyId", copyId)
                .param("articleId", articleId)
                .update();
    }

    /** 发布新版本后提醒所有"已获取该老师该科目课件、且来源内容确实变化"的老师。 */
    @Transactional
    public int notifyCopiersOfNewRelease(long teacherAccountId, String subject) {
        return jdbc.sql("""
                INSERT INTO public.course_notification
                    (recipient_account_id, kind, title, body, source_teacher_account_id, copy_id, article_id)
                SELECT c.owner_account_id, 'source_updated',
                       '来源课件有更新',
                       c.local_title || ' 的来源已发布新版本，可在「共享 · 更新」里查看差异。',
                       :teacherId, c.id, c.source_article_id
                FROM public.course_copy AS c
                JOIN public.course_release_head AS h
                  ON h.teacher_account_id = c.source_teacher_account_id
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = h.teacher_account_id
                 AND ca.article_id = c.source_article_id
                WHERE c.source_teacher_account_id = :teacherId
                  AND ca.subject = :subject
                  AND c.status = 'active'
                  AND ca.content_hash <> c.base_content_hash
                  AND (c.dismissed_hash IS NULL OR ca.content_hash <> c.dismissed_hash)
                """)
                .param("teacherId", teacherAccountId)
                .param("subject", subject)
                .update();
    }

    /** 撤回分享后提醒已获取该科目的老师：来源已不再共享。 */
    @Transactional
    public int notifyRevokedShare(long teacherAccountId, String subject) {
        return jdbc.sql("""
                INSERT INTO public.course_notification
                    (recipient_account_id, kind, title, body, source_teacher_account_id, copy_id, article_id)
                SELECT c.owner_account_id, 'share_revoked',
                       '来源已撤回分享',
                       c.local_title || ' 的来源已撤回分享，已获取的副本仍可继续使用。',
                       :teacherId, c.id, c.source_article_id
                FROM public.course_copy AS c
                JOIN public.course_release_head AS h
                  ON h.teacher_account_id = c.source_teacher_account_id
                JOIN public.course_article AS ca
                  ON ca.release_id = h.release_id
                 AND ca.teacher_account_id = h.teacher_account_id
                 AND ca.article_id = c.source_article_id
                WHERE c.source_teacher_account_id = :teacherId
                  AND ca.subject = :subject
                  AND c.status = 'active'
                """)
                .param("teacherId", teacherAccountId)
                .param("subject", subject)
                .update();
    }

    @Transactional(readOnly = true)
    public List<Notification> list(long recipientAccountId, boolean unreadOnly) {
        return jdbc.sql("""
                SELECT id, kind, title, body, source_teacher_account_id, copy_id, article_id, created_at, read_at
                FROM public.course_notification
                WHERE recipient_account_id = :recipient
                  AND (cast(:unreadOnly as boolean) = false OR read_at IS NULL)
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """)
                .param("recipient", recipientAccountId)
                .param("unreadOnly", unreadOnly)
                .query((rs, rowNum) -> new Notification(
                        rs.getLong("id"),
                        rs.getString("kind"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getObject("source_teacher_account_id", Long.class),
                        rs.getObject("copy_id", Long.class),
                        rs.getObject("article_id", Long.class),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("read_at") != null))
                .list();
    }

    /** 标记已读：{@code ids} 为空表示全部。 */
    @Transactional
    public int markRead(long recipientAccountId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return jdbc.sql("""
                    UPDATE public.course_notification
                    SET read_at = now()
                    WHERE recipient_account_id = :recipient AND read_at IS NULL
                    """)
                    .param("recipient", recipientAccountId)
                    .update();
        }
        return jdbc.sql("""
                UPDATE public.course_notification
                SET read_at = now()
                WHERE recipient_account_id = :recipient AND read_at IS NULL
                  AND id IN (:ids)
                """)
                .param("recipient", recipientAccountId)
                .param("ids", ids)
                .update();
    }

    @Transactional(readOnly = true)
    public long unreadCount(long recipientAccountId) {
        return jdbc.sql("""
                SELECT count(*) FROM public.course_notification
                WHERE recipient_account_id = :recipient AND read_at IS NULL
                """)
                .param("recipient", recipientAccountId)
                .query(Long.class)
                .single();
    }

    /** 一条提醒。 */
    public record Notification(
            long id,
            String kind,
            String title,
            String body,
            Long sourceTeacherId,
            Long copyId,
            Long articleId,
            Instant createdAt,
            boolean read) {
    }
}
