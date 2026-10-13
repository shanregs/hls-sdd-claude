package com.hls.leave.manager;

import com.hls.attendance.manager.ManagerAttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 032 US3 tests: leave types, request bodies and dates that are, and are not, working days. */
abstract class ManagerLeaveTestBase extends ManagerAttendanceTestBase {

    protected static final String LEAVE = ATT + "/leave";
    protected static final String MY_LEAVE = ME + "/leave";

    protected UUID type(String code) {
        return jdbc.queryForObject("select id from leave_type where code = ?", UUID.class, code);
    }

    protected Map<String, Object> body(
            UUID typeId, LocalDate first, LocalDate last, boolean firstHalf, boolean lastHalf, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("leaveTypeId", typeId == null ? null : typeId.toString());
        body.put("firstDate", first.toString());
        body.put("lastDate", last.toString());
        body.put("firstHalf", firstHalf);
        body.put("lastHalf", lastHalf);
        body.put("reason", reason);
        return body;
    }

    protected Resp ask(String token, LocalDate first, LocalDate last) {
        return post(MY_LEAVE, token, body(type("CASUAL"), first, last, false, false, "Family function"));
    }

    /** Two working days in a row in last month, oldest first. */
    protected LocalDate[] twoWorkingDays() {
        List<LocalDate> working = workingDates(lastMonth());
        for (LocalDate d : working) {
            if (working.contains(d.plusDays(1))) {
                return new LocalDate[] {d, d.plusDays(1)};
            }
        }
        throw new IllegalStateException("No two working days in a row in " + lastMonth());
    }

    /** A date in last month that is not a working day (a weekly off). */
    protected LocalDate anOffDay() {
        List<LocalDate> working = workingDates(lastMonth());
        YearMonth month = lastMonth();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            if (!working.contains(month.atDay(d))) {
                return month.atDay(d);
            }
        }
        throw new IllegalStateException("No weekly off in " + month);
    }

    /** A working day, then one or more days off, then a working day: {first, last}. */
    protected LocalDate[] acrossAnOffDay() {
        List<LocalDate> working = workingDates(lastMonth());
        for (LocalDate d : working) {
            LocalDate next = d.plusDays(1);
            if (working.contains(next)) {
                continue;
            }
            LocalDate after = next;
            while (!working.contains(after) && after.isBefore(lastMonth().atEndOfMonth())) {
                after = after.plusDays(1);
            }
            if (working.contains(after)) {
                return new LocalDate[] {d, after};
            }
        }
        throw new IllegalStateException("No working day, day off, working day run in " + lastMonth());
    }

    protected long leaveRows(UUID requestId) {
        return jdbc.queryForObject("select count(*) from manager_day where leave_request_id = ?", Long.class, requestId);
    }

    protected String leaveStatus(UUID requestId) {
        return jdbc.queryForObject("select status from manager_leave_request where id = ?", String.class, requestId);
    }
}
