package com.tutorbase.growth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 成就目录：分层等级，等级越高要求越多，奖励由服务端发放。 */
final class AchievementCatalog {

    enum Metric { CHECKIN, SUBMISSION, WRONG_COLLECTED, MASTERED, READING, UNIQUE_CARDS, OBTAINED_CARDS }

    record Tier(int level, int requirement, int rewardPoints) {
    }

    record Achievement(String key, String title, String description, String icon, String tone,
                       Metric metric, List<Tier> tiers) {
    }

    /** 固定成就（不随卡池规模变化）。 */
    private static final List<Achievement> FIXED = List.of(
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
                    new int[]{20, 30, 50, 80, 120, 180}))
    );

    /** 收藏家：按不重复卡片数计算，等级要求按当前启用卡池总量的百分比缩放。 */
    private static final int[] UNIQUE_CARD_PERCENTAGES = {5, 15, 30, 50, 75, 100};
    private static final int[] CARD_REWARDS = {20, 30, 50, 80, 120, 200};

    /** 欧气收集家：按累计获得卡片数（含重复）计算，固定档位。 */
    private static final Achievement OBTAINED_CARDS = new Achievement(
            "obtained", "欧气收集家", "累计获得的卡片总数", "fas fa-wand-magic-sparkles", "coral",
            Metric.OBTAINED_CARDS, tiers(new int[]{1, 5, 10, 25, 50, 100},
            new int[]{20, 30, 50, 80, 120, 180}));

    private AchievementCatalog() {
    }

    /**
     * 当前卡池规模下的完整成就目录。卡池新增卡组时“收藏家”的档位会自动缩放，
     * 因此不需要修改代码或迁移既有领取记录。
     */
    static List<Achievement> all(int totalCatalogCards) {
        List<Achievement> achievements = new ArrayList<>(FIXED);
        achievements.add(cardsAchievement(totalCatalogCards));
        achievements.add(OBTAINED_CARDS);
        return List.copyOf(achievements);
    }

    static Optional<Achievement> find(int totalCatalogCards, String key) {
        return all(totalCatalogCards).stream()
                .filter(achievement -> achievement.key().equals(key))
                .findFirst();
    }

    static Achievement cardsAchievement(int totalCatalogCards) {
        int total = Math.max(totalCatalogCards, 1);
        int[] requirements = new int[UNIQUE_CARD_PERCENTAGES.length];
        int previous = 0;
        for (int index = 0; index < requirements.length; index++) {
            int scaled = (int) Math.ceil(total * UNIQUE_CARD_PERCENTAGES[index] / 100.0);
            int requirement = Math.min(total, Math.max(scaled, previous + 1));
            requirements[index] = requirement;
            previous = requirement;
        }
        return new Achievement("cards", "收藏家", "收藏室中不重复的卡片数量", "fas fa-images", "butter",
                Metric.UNIQUE_CARDS, tiers(requirements, CARD_REWARDS));
    }

    private static List<Tier> tiers(int[] requirements, int[] rewards) {
        return java.util.stream.IntStream.range(0, requirements.length)
                .mapToObj(index -> new Tier(index + 1, requirements[index], rewards[index]))
                .toList();
    }
}
