package com.hls.attendance.internal;

import java.time.YearMonth;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serializes every write that touches one Teacher-month (spec 008 research.md section 7). A
 * Teacher-month has no row until it is locked, so a row lock cannot protect it; a transaction-scoped
 * advisory lock on the Teacher and month can. Callers re-read the lock state after acquiring it.
 */
@Component
public class TeacherMonthLock {

    private final JdbcTemplate jdbc;
    private final TeacherMonthRepository months;

    public TeacherMonthLock(JdbcTemplate jdbc, TeacherMonthRepository months) {
        this.jdbc = jdbc;
        this.months = months;
    }

    /** Blocks until no other transaction holds this Teacher-month; released at commit or rollback. */
    public void acquire(UUID teacherId, YearMonth month) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", teacherId + ":" + month);
    }

    /** True only while the Teacher-month is LOCKED; a reopened (OPEN) month is editable. */
    public boolean isLocked(UUID teacherId, YearMonth month) {
        return months.findByTeacherIdAndYearMonth(teacherId, month.toString())
                .map(m -> m.getState() == MonthState.LOCKED)
                .orElse(false);
    }
}
