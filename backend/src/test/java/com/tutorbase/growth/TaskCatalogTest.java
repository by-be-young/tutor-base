package com.tutorbase.growth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskCatalogTest {

    @Test
    void keysAreUniqueAndAllRewardsArePositive() {
        assertThat(TaskCatalog.ALL.stream().map(TaskCatalog.Task::key)).doesNotHaveDuplicates();
        assertThat(TaskCatalog.ALL)
                .allSatisfy(task -> {
                    assertThat(task.rewardPoints()).isPositive();
                    assertThat(task.target()).isPositive();
                });
    }

    @Test
    void limitedTaskWindowsAreGenerousEnoughForSchoolDays() {
        var limited = TaskCatalog.ALL.stream()
                .filter(task -> task.period() == TaskCatalog.Period.LIMITED)
                .toList();
        assertThat(limited).isNotEmpty();
        assertThat(limited).allSatisfy(task -> assertThat(task.acceptWindowDays()).isGreaterThanOrEqualTo(7));
    }

    @Test
    void periodicTasksHaveNoAcceptWindow() {
        assertThat(TaskCatalog.ALL.stream()
                .filter(task -> task.period() != TaskCatalog.Period.LIMITED))
                .allSatisfy(task -> assertThat(task.acceptWindowDays()).isZero());
    }
}
