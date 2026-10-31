package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 040 FR-005: a request that starts today and runs on is two requests, only today's approved at once. */
class SameDayLeaveSplitTest extends LeaveTestBase {

    private void requireWorkingToday(World w) {
        Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
        assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
    }

    @Test
    void onlyTodayIsApprovedAndTheLaterDaysWaitForApproval() {
        World w = newWorld();
        requireWorkingToday(w);
        LocalDate end = today().plusDays(6);

        Resp resp = post(ME, w.teacherA().token(), draft(today(), end));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"APPROVED\"").contains("\"sameDay\":true");
        UUID todayId = UUID.fromString(field(resp.body(), "id"));
        List<Map<String, Object>> later = jdbc.queryForList(
                "select id, status, first_date, last_date, same_day from leave_request where linked_request_id = ?",
                todayId);
        assertThat(later).hasSize(1);
        assertThat(later.get(0).get("status")).isEqualTo("PENDING");
        assertThat(later.get(0).get("same_day")).isEqualTo(false);
        assertThat(((java.sql.Date) later.get(0).get("first_date")).toLocalDate()).isAfter(today());
        assertThat(resp.body()).contains("\"otherPart\":{").contains(later.get(0).get("id").toString());
        assertThat(markRow(w.teacherA().teacherId(), today()).get("short_code")).isEqualTo("L");
    }

    @Test
    void rejectingTheLaterPartLeavesTodaysLeaveInPlace() {
        World w = newWorld();
        requireWorkingToday(w);
        Resp resp = post(ME, w.teacherA().token(), draft(today(), today().plusDays(6)));
        UUID todayId = UUID.fromString(field(resp.body(), "id"));
        UUID laterId = jdbc.queryForObject("select id from leave_request where linked_request_id = ?", UUID.class, todayId);

        Resp rejected = post(SUP + "/" + laterId + "/reject", w.managerA().token(), Map.of("reason", "Exams", "version", 0));

        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from leave_request where id = ?", String.class, todayId))
                .isEqualTo("APPROVED");
        assertThat(markRow(w.teacherA().teacherId(), today()).get("short_code")).isEqualTo("L");
    }

    @Test
    void aRangeStartingBeforeTodayIsNotSameDay() {
        World w = newWorld();
        LocalDate mon = monday();

        Resp resp = post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1)));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"PENDING\"").contains("\"sameDay\":false");
    }
}
