package com.tutorbase.rewards;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the rewards interface: callers may read their balance/collection and claim a server-defined milestone.
 * Eligibility, server-owned card selection and persistence are deliberately kept in one transaction.
 */
@Service
class RewardService {

    private final JdbcClient jdbc;

    RewardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    RewardSummary summary(long learnerId) {
        long points = jdbc.sql("SELECT points FROM public.user_points WHERE student_id = :studentId")
                .param("studentId", learnerId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        List<CardReward> cards = jdbc.sql("""
                SELECT id, milestone_points, card_key, set_key, rarity, claimed_at
                FROM public.card_collection
                WHERE student_id = :studentId
                ORDER BY claimed_at DESC, id DESC
                """)
                .param("studentId", learnerId)
                .query(CardReward::map)
                .list();
        return new RewardSummary(points, cards);
    }

    @Transactional
    CardReward claim(long learnerId, long milestonePoints) {
        jdbc.sql("""
                INSERT INTO public.user_points (student_id, points, updated_at)
                VALUES (:studentId, 0, now())
                ON CONFLICT (student_id) DO NOTHING
                """)
                .param("studentId", learnerId)
                .update();

        long points = jdbc.sql("""
                SELECT points FROM public.user_points
                WHERE student_id = :studentId
                FOR UPDATE
                """)
                .param("studentId", learnerId)
                .query(Long.class)
                .single();
        if (points < milestonePoints) {
            throw new RewardMilestoneNotReached();
        }

        Set<String> ownedCardKeys = new HashSet<>(jdbc.sql("""
                SELECT card_key
                FROM public.card_collection
                WHERE student_id = :studentId
                """)
                .param("studentId", learnerId)
                .query(String.class)
                .list());
        RewardCatalog.CardDefinition definition = RewardCatalog.cardFor(milestonePoints, ownedCardKeys);

        return jdbc.sql("""
                INSERT INTO public.card_collection
                    (student_id, milestone_points, card_key, set_key, rarity)
                VALUES (:studentId, :milestonePoints, :cardKey, :setKey, :rarity)
                ON CONFLICT (student_id, milestone_points) DO NOTHING
                RETURNING id, milestone_points, card_key, set_key, rarity, claimed_at
                """)
                .param("studentId", learnerId)
                .param("milestonePoints", milestonePoints)
                .param("cardKey", definition.cardKey())
                .param("setKey", definition.setKey())
                .param("rarity", definition.rarity())
                .query(CardReward::map)
                .optional()
                .orElseThrow(RewardAlreadyClaimed::new);
    }

    record RewardSummary(long points, List<CardReward> collection) {
    }

    record CardReward(long id, long milestonePoints, String cardKey, String setKey, String rarity,
                      Instant claimedAt) {
        static CardReward map(ResultSet resultSet, int rowNumber) throws SQLException {
            return new CardReward(
                    resultSet.getLong("id"),
                    resultSet.getLong("milestone_points"),
                    resultSet.getString("card_key"),
                    resultSet.getString("set_key"),
                    resultSet.getString("rarity"),
                    resultSet.getTimestamp("claimed_at").toInstant());
        }
    }
}
