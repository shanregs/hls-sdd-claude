package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.AttendanceTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers shared by the spec 009 integration tests. */
public abstract class LeaveTestBase extends AttendanceTestBase {

    protected static final String ME = "/api/v1/me/leave";
    protected static final String SUP = "/api/v1/leave";

    protected UUID casual() {
        return jdbc.queryForObject("select id from leave_type where code = 'CASUAL'", UUID.class);
    }

    /**
     * A recent Monday inside the 30-day lookback and the 60-day placement of the test Teachers whose
     * week, and the days the tests use after it (+7, +8, +14, +15), are not holidays. The shared test
     * database may hold the seeded Tamil Nadu holidays, which would change the working-day counts.
     */
    protected LocalDate monday() {
        LocalDate first = today().minusDays(26).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate weekOnly = null;
        for (LocalDate candidate = first; !candidate.isAfter(today().minusDays(6)); candidate = candidate.plusDays(7)) {
            boolean weekClean = !hasHoliday(candidate, candidate.plusDays(6));
            if (weekClean && weekOnly == null) {
                weekOnly = candidate;
            }
            if (weekClean
                    && !hasHoliday(candidate.plusDays(7), candidate.plusDays(8))
                    && !hasHoliday(candidate.plusDays(14), candidate.plusDays(15))) {
                return candidate;
            }
        }
        return weekOnly != null ? weekOnly : first;
    }

    private boolean hasHoliday(LocalDate from, LocalDate to) {
        Integer count = jdbc.queryForObject(
                "select count(*) from attendance_non_working_date where on_date between ? and ?", Integer.class, from, to);
        return count != null && count > 0;
    }

    protected Map<String, Object> draft(LocalDate first, LocalDate last) {
        Map<String, Object> body = new HashMap<>();
        body.put("leaveTypeId", casual().toString());
        body.put("firstDate", first.toString());
        body.put("lastDate", last.toString());
        body.put("halfDayStart", false);
        body.put("halfDayEnd", false);
        body.put("reason", "Pongal travel");
        return body;
    }

    /** Submits a request as the Teacher and returns its id. */
    protected String apply(TeacherCtx teacher, LocalDate first, LocalDate last) {
        Resp resp = post(ME, teacher.token(), draft(first, last));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return field(resp.body(), "id");
    }

    /** The first string value of a JSON field in a flat response body. */
    protected static String field(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"?([^\",}\\]]*)\"?").matcher(body);
        assertThat(m.find()).as("field " + name + " in " + body).isTrue();
        return m.group(1);
    }

    protected void lockMonth(UUID teacher, YearMonth month) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                month.toString());
    }

    protected Map<String, Object> markRow(UUID teacher, LocalDate date) {
        var rows = jdbc.queryForList(
                "select m.leave_request_id, m.set_by_kind, m.day_value, c.short_code from attendance_mark m"
                        + " join attendance_status_code c on c.id = m.status_code_id"
                        + " where m.teacher_id = ? and m.mark_date = ?",
                teacher,
                date);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
