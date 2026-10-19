package com.tutorbase.growth;

import java.util.List;
import java.util.Optional;

/** 成就目录：分层等级，等级越高要求越多，奖励由服务端发放。 */
final class AchievementCatalog {

    enum Metric { CHECKIN, SUBMISSION, WRONG_COLLECTED, MASTERED, READING, CARDS }

    record Tier(int level, int requirement, int rewardPoints) {
    }

    record Achievement(String key, String title, String description, String icon, String tone,
                       Metric metric, List<Tier> tiers) {
    }

    static final List<Achievement> ALL = List.of(
            new Achievement("checkin", "签到足迹", "累计完成每日签到", "fas fa-calendar-check", "butter",
                    Metric.CHECKIN, tiers(new int[]{1, 3, 7, 14, 30, 60, 100},
                    new int[]{20, 30, 50, 80, 120, 180, 260})),
            new Achievement("submission", "提笔作答", "累计提交的作答次数", "fas fa-pen-nib", "coral",
                    Metric.SUBMISSION, tiers(new int[]{1, 5, 15, 30, 60, 120},
                    new int[]{20, 30, 50, 80, 120, 180})),
            new Achievement("mastery", "温故知新", "累计掌握的错题数量", "fas fa-check-double", "lime",
                    Metric.MASTERED, tiers(new int[]{1, 3, 5, 10, 20, 40},
                    new int[]{30, 40, 60, 100, 160, 240})),
            new Achievement("collection", "错题收藏家", "累计收集的错题数量", "fas fa-layer-group", "sky",
                    Metric.WRONG_COLLECTED, tiers(new int[]{1, 5, 10, 20, 40, 80},
                    new int[]{20, 30, 50, 80, 120, 180})),
            new Achievement("reading", "博览群书", "完整完成的学习文章数量", "fas fa-book-open", "pink",
                    Metric.READING, tiers(new int[]{1, 3, 5, 10, 20, 40},
                    new int[]{20, 30, 50, 80, 120, 180})),
            new Achievement("cards", "收藏家", "收藏室里的卡片数量", "fas fa-images", "butter",
                    Metric.CARDS, tiers(new int[]{1, 3, 6, 12, 24, 35},
                    new int[]{20, 30, 50, 80, 120, 200}))
    );

    private AchievementCatalog() {
    }

    static Optional<Achievement> find(String key) {
        return ALL.stream().filter(achievement -> achievement.key().equals(key)).findFirst();
    }

    private static List<Tier> tiers(int[] requirements, int[] rewards) {
        return java.util.stream.IntStream.range(0, requirements.length)
                .mapToObj(index -> new Tier(index + 1, requirements[index], rewards[index]))
                .toList();
    }
}
