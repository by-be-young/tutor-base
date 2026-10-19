package com.tutorbase.growth;

import java.util.List;
import java.util.Optional;

/**
 * 任务目录：每日 / 每周自动刷新，以及需要接取的限时任务。
 * 限时任务的截止时间刻意放宽，避免学生工作日无法完成。
 */
final class TaskCatalog {

    enum Period { DAILY, WEEKLY, LIMITED }

    enum Metric { CHECKIN, SUBMISSION, WRONG_COLLECTED, WRONG_MASTERED }

    record Task(String key, Period period, String title, String description, String icon, String tone,
                Metric metric, int target, int rewardPoints, int acceptWindowDays) {
    }

    static final List<Task> ALL = List.of(
            new Task("daily-submit", Period.DAILY, "每日动笔", "提交任意 2 道作答", "fas fa-pen-nib",
                    "coral", Metric.SUBMISSION, 2, 20, 0),
            new Task("daily-collect", Period.DAILY, "随手记错", "收集任意 1 道错题", "fas fa-layer-group",
                    "sky", Metric.WRONG_COLLECTED, 1, 20, 0),
            new Task("daily-master", Period.DAILY, "今日消灭", "掌握任意 1 道错题", "fas fa-check-double",
                    "lime", Metric.WRONG_MASTERED, 1, 30, 0),

            new Task("weekly-submit", Period.WEEKLY, "本周动笔", "本周累计提交 8 道作答", "fas fa-pen-nib",
                    "coral", Metric.SUBMISSION, 8, 80, 0),
            new Task("weekly-master", Period.WEEKLY, "温故周计划", "本周累计掌握 3 道错题", "fas fa-check-double",
                    "lime", Metric.WRONG_MASTERED, 3, 100, 0),
            new Task("weekly-checkin", Period.WEEKLY, "本周打卡", "本周签到 5 天", "fas fa-calendar-check",
                    "butter", Metric.CHECKIN, 5, 80, 0),

            new Task("sprint-submit", Period.LIMITED, "七日动笔挑战", "接取后 7 天内提交 6 道作答",
                    "fas fa-rocket", "coral", Metric.SUBMISSION, 6, 120, 7),
            new Task("sprint-master", Period.LIMITED, "十日温故计划", "接取后 10 天内掌握 2 道错题",
                    "fas fa-book-medical", "lime", Metric.WRONG_MASTERED, 2, 150, 10),
            new Task("sprint-collect", Period.LIMITED, "错题整理周", "接取后 7 天内收集 3 道错题",
                    "fas fa-layer-group", "sky", Metric.WRONG_COLLECTED, 3, 100, 7)
    );

    private TaskCatalog() {
    }

    static Optional<Task> find(String key) {
        return ALL.stream().filter(task -> task.key().equals(key)).findFirst();
    }
}
