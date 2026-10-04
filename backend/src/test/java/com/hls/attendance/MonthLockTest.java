package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Spec 008 US6: lock, reopen and relock. Each test uses its own old month (2010 onwards) in which only
 * its own Teachers are placed, because a lock covers every Teacher placed in the month.
 */
class MonthLockTest extends AttendanceTestBase {

    private static final AtomicInteger NEXT_MONTH = new AtomicInteger(0);
    private static final String PRESENT = "00000000-0000-0000-0008-000000000001";
    private static final String BASE = "/api/v1/attendance";

    private static YearMonth freshMonth() {
        return YearMonth.of(2010, 1).plusMonths(NEXT_MONTH.getAndIncrement());
    }

    private record Fixture(String admin, UUID school, UUID teacherId, String teacherToken, YearMonth month) {}

    /** A Teacher placed at a School for the whole of an otherwise empty old month, with nothing marked. */
    private Fixture fixture(LocalDate endsOn) {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        TeacherCtx teacher = newTeacher(admin, null, 0);
        YearMonth month = freshMonth();
        jdbc.update(
                "insert into teacher_placement (id, teacher_id, school_id, starts_on, ends_on, status, created_at)"
                        + " values (?, ?, ?, ?, ?, 'ACTIVE', now())",
                UUID.randomUUID(),
                teacher.teacherId(),
                school,
                month.atDay(1),
                endsOn == null ? month.atEndOfMonth() : endsOn);
        return new Fixture(admin, school, teacher.teacherId(), teacher.token(), month);
    }

    /** Marks Present on every non-Sunday day up to {@code lastDay} (the default weekly off is Sunday). */
    private void markWorkingDays(Fixture f, int lastDay) {
        jdbc.update(
                "insert into attendance_mark (id, teacher_id, mark_date, status_code_id, day_value, school_id,"
                        + " set_by_user_id, set_by_kind, set_at, version)"
                        + " select gen_random_uuid(), ?, d::date, ?::uuid, 1.00, ?, ?, 'SUPERVISOR', now(), 0"
                        + " from generate_series(?::date, ?::date, interval '1 day') d"
                        + " where extract(isodow from d) <> 7",
                f.teacherId(),
                PRESENT,
                f.school(),
                UUID.randomUUID(),
                f.month().atDay(1),
                f.month().atDay(lastDay));
    }

    private Resp lock(String token, YearMonth month) {
        return post(BASE + "/months/" + month + "/lock", token, Map.of());
    }

    private String teacherUrl(Fixture f) {
        return BASE + "/teachers/" + f.teacherId();
    }

    private long lockedRows(UUID teacherId) {
        return jdbc.queryForObject(
                "select count(*) from attendance_teacher_month where teacher_id = ? and state = 'LOCKED'",
                Long.class,
                teacherId);
    }

    @Test
    void theCurrentMonthCannotBeLocked() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = lock(admin, YearMonth.from(today()));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("after it has ended");
    }

    @Test
    void anUnmarkedWorkingDayRefusesTheLockAndListsTheTeacherAndDates() {
        Fixture f = fixture(null);
        markWorkingDays(f, 20); // days 21..31 are still unmarked

        Resp resp = lock(f.admin(), f.month());

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains(f.teacherId().toString()).contains(f.month().atDay(22).toString());
        assertThat(resp.body()).doesNotContain(f.month().atDay(5).toString());
        assertThat(lockedRows(f.teacherId())).isZero();
    }

    @Test
    void aFullyMarkedMonthLocksFreezesTheRollupAndRefusesEveryEdit() {
        Fixture f = fixture(null);
        markWorkingDays(f, f.month().lengthOfMonth());

        Resp locked = lock(f.admin(), f.month());

        assertThat(locked.status()).as(locked.body()).isEqualTo(200);
        assertThat(locked.map()).containsEntry("locked", 1);
        Map<String, Object> view = get(teacherUrl(f) + "?month=" + f.month(), f.admin()).map();
        assertThat(view).containsEntry("locked", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> rollup = (Map<String, Object>) view.get("rollup");
        assertThat(rollup).containsEntry("frozen", true).containsEntry("unmarked", 0);
        assertThat(((Number) rollup.get("daysWorked")).intValue()).isEqualTo(26); // Jan 2010: 31 days, 5 Sundays
        // Nobody can change a day directly, whatever their role.
        String mark = teacherUrl(f) + "/marks/" + f.month().atDay(3);
        Map<String, Object> body = Map.of("statusCode", "L", "dayValue", 1);
        assertThat(put(mark, f.admin(), body).status()).isEqualTo(409);
        assertThat(put(mark, f.admin(), body).body()).contains("locked");
        assertThat(delete(mark, f.admin()).status()).isEqualTo(409);
        assertThat(get(BASE + "/teachers/" + f.teacherId() + "/months/" + f.month() + "/events", f.admin()).body())
                .contains("LOCKED");
    }

    @Test
    void reopenNeedsAReasonThenAllowsCorrectionsAndRelockRefreezes() {
        Fixture f = fixture(null);
        markWorkingDays(f, f.month().lengthOfMonth());
        lock(f.admin(), f.month());
        String reopen = teacherUrl(f) + "/months/" + f.month() + "/reopen";
        String relock = teacherUrl(f) + "/months/" + f.month() + "/relock";
        String day = teacherUrl(f) + "/marks/" + f.month().atDay(4 + 1);

        assertThat(post(reopen, f.admin(), Map.of("reason", " ")).status()).isEqualTo(400);
        Resp reopened = post(reopen, f.admin(), Map.of("reason", "Wrong leave day"));
        assertThat(reopened.status()).as(reopened.body()).isEqualTo(200);
        // A correction is now possible; clearing a day leaves a gap that blocks the relock.
        assertThat(delete(day, f.admin()).status()).isEqualTo(204);
        Resp gap = post(relock, f.admin(), Map.of());
        assertThat(gap.status()).isEqualTo(409);
        assertThat(gap.body()).contains(f.month().atDay(5).toString());
        // Filling the day with Leave, then relocking, refreezes with the new figures.
        assertThat(put(day, f.admin(), Map.of("statusCode", "L", "dayValue", 1)).status()).isEqualTo(200);
        Resp relocked = post(relock, f.admin(), Map.of());
        assertThat(relocked.status()).as(relocked.body()).isEqualTo(200);
        assertThat(relocked.map()).containsEntry("frozen", true);
        assertThat(((Number) relocked.map().get("daysLeave")).intValue()).isEqualTo(1);
        assertThat(((Number) relocked.map().get("daysWorked")).intValue()).isEqualTo(25);
        // The history shows lock, reopen with its reason, relock in order.
        String events = get(teacherUrl(f) + "/months/" + f.month() + "/events", f.admin()).body();
        assertThat(events.indexOf("LOCKED")).isLessThan(events.indexOf("REOPENED"));
        assertThat(events.indexOf("REOPENED")).isLessThan(events.indexOf("RELOCKED"));
        assertThat(events).contains("Wrong leave day");
        assertChangeRecorded(f.admin(), "ATTENDANCE_MONTH", f.teacherId() + ":" + f.month(), "state");
    }

    @Test
    void reopeningAMonthThatIsNotLockedIsRefused() {
        Fixture f = fixture(null);

        Resp resp = post(teacherUrl(f) + "/months/" + f.month() + "/reopen", f.admin(), Map.of("reason", "x"));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(post(teacherUrl(f) + "/months/" + f.month() + "/relock", f.admin(), Map.of()).status())
                .isEqualTo(409);
    }

    @Test
    void onlyAdminAndDirectorMayLockReopenAndRelock() {
        Fixture f = fixture(null);
        TeacherCtx teacher = newTeacher(f.admin(), null, 0);
        String manager = signInAs(Role.MANAGER).token();
        String system = signInAs(Role.SYSTEM).token();

        for (String token : List.of(manager, system, teacher.token())) {
            assertThat(lock(token, f.month()).status()).isEqualTo(403);
            assertThat(post(teacherUrl(f) + "/months/" + f.month() + "/reopen", token, Map.of("reason", "x")).status())
                    .isEqualTo(403);
            assertThat(post(teacherUrl(f) + "/months/" + f.month() + "/relock", token, Map.of()).status())
                    .isEqualTo(403);
        }
        String director = signInAs(Role.DIRECTOR).token();
        markWorkingDays(f, f.month().lengthOfMonth());
        assertThat(lock(director, f.month()).status()).isEqualTo(200);
        assertThat(lock(null, f.month()).status()).isEqualTo(401);
    }

    @Test
    void aMonthWithNobodyPlacedLocksTrivially() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = lock(admin, YearMonth.of(2009, 1));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("locked", 0);
    }

    @Test
    void aTeacherWhoLeftMidMonthOnlyNeedsDaysUpToTheirLastPlacementDay() {
        // Placed until the 14th: days 1..14 (minus Sundays) must be marked, the rest are not required.
        Fixture f = fixture(null);
        jdbc.update(
                "update teacher_placement set ends_on = ? where teacher_id = ?",
                f.month().atDay(14),
                f.teacherId());
        markWorkingDays(f, 14);

        Resp resp = lock(f.admin(), f.month());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @Test
    void changingWeeklyOffDaysAfterTheLockDoesNotChangeTheFrozenRollup() {
        Fixture f = fixture(null);
        markWorkingDays(f, f.month().lengthOfMonth());
        lock(f.admin(), f.month());
        Map<String, Object> calendar = get(BASE + "/calendar", f.admin()).map();
        long version = ((Number) calendar.get("defaultVersion")).longValue();
        try {
            put(BASE + "/calendar/default", f.admin(), Map.of("weeklyOff", List.of("SUN", "SAT"), "version", version));

            String body = get(teacherUrl(f) + "?month=" + f.month(), f.admin()).body();

            assertThat(body).contains("\"workingDays\":26");
        } finally {
            long current = ((Number) get(BASE + "/calendar", f.admin()).map().get("defaultVersion")).longValue();
            put(BASE + "/calendar/default", f.admin(), Map.of("weeklyOff", List.of("SUN"), "version", current));
        }
    }

    @Test
    void aMarkRacingTheLockNeverLeavesTheFrozenRollupOutOfStepWithTheMarks() throws Exception {
        for (int round = 0; round < 3; round++) {
            Fixture f = fixture(null);
            markWorkingDays(f, f.month().lengthOfMonth());
            String day = teacherUrl(f) + "/marks/" + f.month().atDay(6);

            CompletableFuture<Resp> locking = CompletableFuture.supplyAsync(() -> lock(f.admin(), f.month()));
            CompletableFuture<Resp> editing =
                    CompletableFuture.supplyAsync(() -> put(day, f.admin(), Map.of("statusCode", "L", "dayValue", 1)));
            Resp lockResp = locking.get();
            Resp editResp = editing.get();

            assertThat(lockResp.status()).as(lockResp.body()).isEqualTo(200);
            Long leaveInDb = jdbc.queryForObject(
                    "select count(*) from attendance_mark m join attendance_status_code c on c.id = m.status_code_id"
                            + " where m.teacher_id = ? and c.short_code = 'L'",
                    Long.class,
                    f.teacherId());
            Number frozenLeave = jdbc.queryForObject(
                    "select days_leave from attendance_teacher_month where teacher_id = ?", Number.class, f.teacherId());
            assertThat(frozenLeave.longValue())
                    .as("frozen leave equals the leave marks left in the database (edit status %s)", editResp.status())
                    .isEqualTo(leaveInDb);
            assertThat(editResp.status()).isIn(200, 409);
        }
    }
}
