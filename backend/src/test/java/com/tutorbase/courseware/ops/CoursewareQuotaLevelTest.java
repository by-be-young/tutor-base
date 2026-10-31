package com.tutorbase.courseware.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 容量水位阈值：80% 告警、95% 严重，边界值必须落在更高的那一档。 */
class CoursewareQuotaLevelTest {

    @Test
    void mapsRatiosToLevels() {
        assertThat(CoursewareMaintenanceService.level(0d)).isEqualTo("ok");
        assertThat(CoursewareMaintenanceService.level(0.79)).isEqualTo("ok");
        assertThat(CoursewareMaintenanceService.level(0.8)).isEqualTo("warn");
        assertThat(CoursewareMaintenanceService.level(0.94)).isEqualTo("warn");
        assertThat(CoursewareMaintenanceService.level(0.95)).isEqualTo("critical");
        assertThat(CoursewareMaintenanceService.level(1.2)).isEqualTo("critical");
    }
}
