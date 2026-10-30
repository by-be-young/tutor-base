package com.tutorbase.shared.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 只读的学员认领归属判定，供身份与统计模块在服务层复用。
 *
 * <p>认领关系由 {@code student.claimed_by} 唯一确定；本组件不写库，也不暴露他人身份。
 */
@Component
public final class LearnerClaimAccess {

    public enum State {
        LEARNER_NOT_FOUND,
        UNCLAIMED,
        CLAIMED_BY_OTHER_ADMINISTRATOR,
        CLAIMED_BY_CURRENT_ADMINISTRATOR
    }

    private final JdbcClient jdbc;

    public LearnerClaimAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public State state(long learnerId, long administratorAccountId) {
        return jdbc.sql("""
                SELECT claimed_by
                FROM public.student
                WHERE id = :learnerId
                """)
                .param("learnerId", learnerId)
                .query((resultSet, rowNumber) ->
                        new ClaimRow(resultSet.getObject("claimed_by", Long.class)))
                .optional()
                .map(claim -> {
                    if (claim.claimedByAccountId() == null) {
                        return State.UNCLAIMED;
                    }
                    return claim.claimedByAccountId() == administratorAccountId
                            ? State.CLAIMED_BY_CURRENT_ADMINISTRATOR
                            : State.CLAIMED_BY_OTHER_ADMINISTRATOR;
                })
                .orElse(State.LEARNER_NOT_FOUND);
    }

    private record ClaimRow(Long claimedByAccountId) {
    }
}
