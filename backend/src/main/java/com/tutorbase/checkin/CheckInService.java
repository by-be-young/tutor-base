package com.tutorbase.checkin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每日签到：签到记录、月度统计与积分发放全部在服务端完成。
 * <ul>
 *   <li>「今日」以 Asia/Shanghai 时区判定（注入的 {@link ZoneId}）。</li>
 *   <li>按自然月统计；第 7、14 次签到当天额外 +100 积分。</li>
 *   <li>积分原子累加到 public.user_points（与前端任务/里程碑共用）。</li>
 *   <li>同日重复签到幂等：并发双请求中落败的一方回读胜者记录并返回已签到状态，绝不重复加分。</li>
 * </ul>
 */
@Service
class CheckInService {

    static final int BASE_POINTS = 100;
    static final int BONUS_POINTS = 100;
    static final List<Integer> BONUS_MILESTONES = List.of(7, 14);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    CheckInService(JdbcClient jdbc, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** 月度概览：该月已签到的日期、本月签到次数、今日是否已签。 */
    MonthOverview overview(long learnerId, YearMonth yearMonth) {
        LocalDate today = today();
        List<LocalDate> dates = findDates(learnerId, yearMonth);
        boolean inMonth = yearMonth.equals(YearMonth.from(today));
        boolean todayCheckedIn = inMonth && dates.contains(today);
        return new MonthOverview(dates, todayCheckedIn, today);
    }

    /** 执行当日签到（幂等）。 */
    @Transactional
    CheckInResult checkIn(long learnerId) {
        LocalDate today = today();
        Optional<StoredCheckIn> existing = findToday(learnerId, today);
        if (existing.isPresent()) {
            return resultFor(learnerId, existing.get(), today, true);
        }

        int next = countMonth(learnerId, today) + 1;
        int bonusDay = BONUS_MILESTONES.contains(next) ? next : 0;
        long awarded = BASE_POINTS + (bonusDay > 0 ? BONUS_POINTS : 0);

        try {
            jdbc.sql("""
                    INSERT INTO public.daily_check_in
                        (student_id, check_in_date, points_awarded, bonus_days)
                    VALUES (:studentId, :checkInDate, :awarded, :bonusDay)
                    """)
                    .param("studentId", learnerId)
                    .param("checkInDate", today)
                    .param("awarded", awarded)
                    .param("bonusDay", bonusDay > 0 ? bonusDay : null)
                    .update();
        } catch (DuplicateKeyException race) {
            // 并发请求已插入今日记录：以对方的记录为准，避免重复加分。
            StoredCheckIn winner = findToday(learnerId, today).orElseThrow();
            return resultFor(learnerId, winner, today, true);
        }

        long totalPoints = jdbc.sql("""
                INSERT INTO public.user_points (student_id, points, updated_at)
                VALUES (:studentId, :awarded, now())
                ON CONFLICT (student_id) DO UPDATE
                    SET points = user_points.points + EXCLUDED.points, updated_at = now()
                RETURNING points
                """)
                .param("studentId", learnerId)
                .param("awarded", awarded)
                .query(Long.class)
                .single();

        return new CheckInResult(today, awarded, bonusDay > 0 ? bonusDay : null, next, false, totalPoints);
    }

    private Optional<StoredCheckIn> findToday(long learnerId, LocalDate today) {
        return jdbc.sql("""
                SELECT points_awarded, bonus_days FROM public.daily_check_in
                WHERE student_id = :studentId AND check_in_date = :checkInDate
                """)
                .param("studentId", learnerId)
                .param("checkInDate", today)
                .query(StoredCheckIn::map)
                .optional();
    }

    private List<LocalDate> findDates(long learnerId, YearMonth yearMonth) {
        return jdbc.sql("""
                SELECT check_in_date FROM public.daily_check_in
                WHERE student_id = :studentId
                  AND check_in_date >= :monthStart
                  AND check_in_date < :monthEnd
                ORDER BY check_in_date
                """)
                .param("studentId", learnerId)
                .param("monthStart", yearMonth.atDay(1))
                .param("monthEnd", yearMonth.plusMonths(1).atDay(1))
                .query(LocalDate.class)
                .list();
    }

    private int countMonth(long learnerId, LocalDate day) {
        YearMonth yearMonth = YearMonth.from(day);
        return jdbc.sql("""
                SELECT count(*) FROM public.daily_check_in
                WHERE student_id = :studentId
                  AND check_in_date >= :monthStart
                  AND check_in_date < :monthEnd
                """)
                .param("studentId", learnerId)
                .param("monthStart", yearMonth.atDay(1))
                .param("monthEnd", yearMonth.plusMonths(1).atDay(1))
                .query(Long.class)
                .single()
                .intValue();
    }

    /** 幂等路径的结果：按已有记录回填，并重新统计本月次数与累计积分。 */
    private CheckInResult resultFor(long learnerId, StoredCheckIn row, LocalDate today, boolean alreadyCheckedIn) {
        int monthCount = countMonth(learnerId, today);
        long totalPoints = jdbc.sql("""
                SELECT points FROM public.user_points WHERE student_id = :studentId
                """)
                .param("studentId", learnerId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return new CheckInResult(today, row.pointsAwarded(), row.bonusDays(), monthCount, alreadyCheckedIn, totalPoints);
    }

    record MonthOverview(List<LocalDate> dates, boolean todayCheckedIn, LocalDate today) {
    }

    record StoredCheckIn(long pointsAwarded, Integer bonusDays) {
        static StoredCheckIn map(ResultSet resultSet, int rowNumber) throws SQLException {
            long points = resultSet.getLong("points_awarded");
            int bonus = resultSet.getInt("bonus_days");
            return new StoredCheckIn(points, resultSet.wasNull() ? null : bonus);
        }
    }

    record CheckInResult(LocalDate date, long pointsAwarded, Integer bonusDays,
                         int monthCount, boolean alreadyCheckedIn, long totalPoints) {
    }
}
