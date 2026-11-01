package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017b US2: a day record per allocation, marked by the Zone Manager, locked per substitute and month. */
class DayApiTest extends SubstitutionTestBase {

    private static final String PAYOUTS = "/api/v1/substitute-payouts";

    private UUID allocate(World w, TeacherCtx substitute) {
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID requirement = requirementId(w.teacherA().teacherId(), day);
        assertThat(assign(w.managerA().token(), requirement, substitute.teacherId()).status()).isEqualTo(200);
        return dayOf(requirement, substitute.teacherId());
    }

    private UUID dayOf(UUID requirement, UUID substitute) {
        return jdbc.queryForObject(
                "select id from substitute_day where requirement_id = ? and substitute_teacher_id = ?",
                UUID.class,
                requirement,
                substitute);
    }

    private long dayVersion(UUID day) {
        return jdbc.queryForObject("select version from substitute_day where id = ?", Long.class, day);
    }

    private Resp mark(String token, UUID day, String attendance) {
        return put(PAYOUTS + "/days/" + day + "/attendance", token,
                Map.of("attendance", attendance, "version", dayVersion(day)));
    }

    /** A day of a Substitute in an ended month, inserted directly so the lock rules can be exercised. */
    private UUID pastDay(UUID substitute, LocalDate date, String attendance) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into substitute_day (id, requirement_id, substitute_teacher_id, school_id, zone_id, absent_teacher_id,
                    day_date, day_value, live, attendance, marked_by, marked_at, source, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 1.00, true, ?, ?, ?, 'ALLOCATION', 'ALLOCATED', 0, now(), now())
                """,
                id, UUID.randomUUID(), substitute, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), date,
                attendance, "NOT_MARKED".equals(attendance) ? null : UUID.randomUUID(),
                "NOT_MARKED".equals(attendance) ? null : java.sql.Timestamp.from(java.time.Instant.now()));
        return id;
    }

    @Test
    void assigningCreatesOneDayRecordInTheSchoolsZone() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);

        UUID day = allocate(w, substitute);

        Map<String, Object> row = jdbc.queryForMap("select * from substitute_day where id = ?", day);
        assertThat(row.get("attendance")).isEqualTo("NOT_MARKED");
        assertThat(row.get("status")).isEqualTo("ALLOCATED");
        assertThat(row.get("live")).isEqualTo(true);
        assertThat(row.get("zone_id")).isEqualTo(w.zoneA());
        assertThat(row.get("school_id")).isEqualTo(w.schoolA());
        assertThat(row.get("absent_teacher_id")).isEqualTo(w.teacherA().teacherId());
        assertThat(jdbc.queryForObject("select count(*) from substitute_day_event where day_id = ?", Integer.class, day))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void theZoneManagerMarksPresentAndTheStatusAndHistoryFollow() {
        World w = newWorld();
        UUID day = allocate(w, benchTeacher(w));

        Resp resp = mark(w.managerA().token(), day, "PRESENT");

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select attendance from substitute_day where id = ?", String.class, day))
                .isEqualTo("PRESENT");
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, day))
                .isEqualTo("ATTENDED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day_event where day_id = ? and field = 'ATTENDANCE'",
                        Integer.class, day))
                .isEqualTo(1);
        assertThat(mark(w.managerA().token(), day, "ABSENT").status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, day))
                .isEqualTo("DID_NOT_ATTEND");
    }

    @Test
    void adminAndDirectorMarkAnywhereAndOtherZonesAndTeachersAreTurnedAway() {
        World w = newWorld();
        UUID day = allocate(w, benchTeacher(w));

        assertThat(mark(w.managerB().token(), day, "PRESENT").status()).as("other Zone").isEqualTo(404);
        assertThat(mark(w.teacherA().token(), day, "PRESENT").status()).as("Teacher").isEqualTo(403);
        assertThat(mark(signInAs(Role.SYSTEM).token(), day, "PRESENT").status()).as("System").isEqualTo(403);
        assertThat(mark(w.admin(), day, "PRESENT").status()).as("Admin").isEqualTo(200);
        assertThat(mark(w.director(), day, "ABSENT").status()).as("Director").isEqualTo(200);
        assertThat(get(PAYOUTS + "/days/" + day, w.managerB().token()).status()).isEqualTo(404);
    }

    @Test
    void aStaleVersionACancelledDayAndAFutureDayAreRefused() {
        World w = newWorld();
        UUID day = allocate(w, benchTeacher(w));

        Resp stale = put(PAYOUTS + "/days/" + day + "/attendance", w.managerA().token(),
                Map.of("attendance", "PRESENT", "version", 99));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(mark(w.managerA().token(), day, "MAYBE").status()).isEqualTo(400);

        UUID future = pastDay(UUID.randomUUID(), today().plusDays(3), "NOT_MARKED");
        jdbc.update("update substitute_day set school_id = ? where id = ?", w.schoolA(), future);
        Resp tooSoon = mark(w.managerA().token(), future, "PRESENT");
        assertThat(tooSoon.status()).isEqualTo(409);
        assertThat(tooSoon.body()).contains("future");

        UUID requirement = jdbc.queryForObject("select requirement_id from substitute_day where id = ?", UUID.class, day);
        assertThat(release(w.managerA().token(), requirement, "Teacher is back").status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select live from substitute_day where id = ?", Boolean.class, day)).isFalse();
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, day))
                .isEqualTo("CANCELLED");
        assertThat(mark(w.managerA().token(), day, "PRESENT").status()).isEqualTo(409);
    }

    @Test
    void aMonthLocksOnlyWhenEndedAndCompleteAndReopensWithAReason() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        YearMonth month = YearMonth.from(today()).minusMonths(1);
        UUID first = pastDay(substitute.teacherId(), month.atDay(3), "PRESENT");
        UUID second = pastDay(substitute.teacherId(), month.atDay(4), "NOT_MARKED");
        String lock = PAYOUTS + "/months/" + substitute.teacherId() + "/" + month + "/lock";

        Resp blocked = post(lock, w.admin(), Map.of());
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.body()).contains(month.atDay(4).toString());
        assertThat(post(lock, w.managerA().token(), Map.of()).status()).as("Manager has no Approve").isEqualTo(403);

        jdbc.update("update substitute_day set attendance = 'ABSENT', marked_by = ?, marked_at = now() where id = ?",
                UUID.randomUUID(), second);
        assertThat(post(lock, w.director(), Map.of()).status()).isEqualTo(200);
        assertThat(post(lock, w.admin(), Map.of()).status()).as("already locked").isEqualTo(409);
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, first))
                .isNotEqualTo("ATTENDED");

        String reopen = PAYOUTS + "/months/" + substitute.teacherId() + "/" + month + "/reopen";
        assertThat(post(reopen, w.admin(), Map.of("reason", " ")).status()).isEqualTo(400);
        assertThat(post(reopen, w.admin(), Map.of("reason", "x".repeat(501))).status()).isEqualTo(400);
        assertThat(post(reopen, w.admin(), Map.of("reason", "Wrong day marked")).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, first))
                .isEqualTo("ATTENDED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day_event where day_id = ? and field = 'REOPEN'",
                        Integer.class, first))
                .isEqualTo(1);
    }

    @Test
    void thisMonthCannotBeLockedYet() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        pastDay(substitute.teacherId(), today(), "PRESENT");

        Resp resp = post(PAYOUTS + "/months/" + substitute.teacherId() + "/" + YearMonth.from(today()) + "/lock",
                w.admin(), Map.of());

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("after it has ended");
    }

    @Test
    void aLockedMonthRefusesMarkingUntilReopened() {
        World w = newWorld();
        UUID day = allocate(w, benchTeacher(w));
        UUID substitute = jdbc.queryForObject("select substitute_teacher_id from substitute_day where id = ?", UUID.class, day);
        LocalDate date = jdbc.queryForObject("select day_date from substitute_day where id = ?", LocalDate.class, day);
        org.junit.jupiter.api.Assumptions.assumeTrue(YearMonth.from(date).isBefore(YearMonth.from(today())),
                "the test Monday is in the current month, so it cannot be locked yet");
        assertThat(mark(w.managerA().token(), day, "PRESENT").status()).isEqualTo(200);
        assertThat(post(PAYOUTS + "/months/" + substitute + "/" + YearMonth.from(date) + "/lock", w.admin(), Map.of()).status())
                .isEqualTo(200);

        Resp locked = mark(w.managerA().token(), day, "ABSENT");

        assertThat(locked.status()).isEqualTo(409);
        assertThat(locked.body()).contains("locked");
    }

    @Test
    void dayEventsCannotBeChangedOrDeleted() {
        World w = newWorld();
        UUID day = allocate(w, benchTeacher(w));

        assertThatThrownBy(() -> jdbc.update("update substitute_day_event set note = 'x' where day_id = ?", day))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from substitute_day_event where day_id = ?", day))
                .hasMessageContaining("append-only");
    }

    @Test
    void aSubstituteCannotBeLinkedToAnAccountOrPlacedAndOnlyOneDayExistsPerAllocation() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);

        Resp link = put("/api/v1/teachers/" + substitute.teacherId() + "/user", w.admin(),
                Map.of("userId", substitute.signed().userId()));
        assertThat(link.status()).isEqualTo(409);
        Resp place = placeTeacherRaw(w.admin(), substitute.teacherId(), w.schoolA(), today());
        assertThat(place.status()).isEqualTo(409);

        UUID day = allocate(w, substitute);
        UUID requirement = jdbc.queryForObject("select requirement_id from substitute_day where id = ?", UUID.class, day);
        assertThat(release(w.managerA().token(), requirement, "Changed my mind").status()).isEqualTo(200);
        assertThat(assign(w.managerA().token(), requirement, substitute.teacherId()).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day where requirement_id = ? and substitute_teacher_id = ?",
                        Integer.class, requirement, substitute.teacherId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select live from substitute_day where id = ?", Boolean.class, day)).isTrue();
    }

    @Test
    void aSubstituteCannotBeCreatedWithAnAccountAndOnlyAdminAndDirectorSetTheEngagement() {
        World w = newWorld();
        TeacherCtx someone = newTeacher(w.admin(), null, 0);

        Resp withAccount = post("/api/v1/teachers", w.admin(),
                Map.of("name", "Sub With Login", "phone", "9876543210", "engagement", "SUBSTITUTE",
                        "userId", someone.signed().userId()));
        assertThat(withAccount.status()).as(withAccount.body()).isEqualTo(400);

        Resp ok = post("/api/v1/teachers", w.admin(),
                Map.of("name", "Sub Without Login", "phone", "9876543211", "engagement", "SUBSTITUTE"));
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
        assertThat(ok.body()).contains("\"engagement\":\"SUBSTITUTE\"").contains("\"userId\":null");

        long version = jdbc.queryForObject("select version from teacher where id = ?", Long.class, someone.teacherId());
        Map<String, Object> body = Map.of("engagement", "SUBSTITUTE", "version", version);
        String uri = "/api/v1/teachers/" + someone.teacherId() + "/engagement";
        assertThat(put(uri, w.managerA().token(), body).status()).as("Zone Manager").isEqualTo(403);
        assertThat(put(uri, w.teacherA().token(), body).status()).as("Teacher").isEqualTo(403);
        assertThat(put(uri, w.admin(), Map.of("engagement", "CASUAL", "version", version)).status()).isEqualTo(400);
        assertThat(put(uri, w.director(), body).status()).as("Director").isEqualTo(200);
        assertThat(jdbc.queryForObject("select user_id from teacher where id = ?", UUID.class, someone.teacherId()))
                .as("becoming a Substitute clears the account link")
                .isNull();
    }

    @Test
    void aPlacedTeacherCannotBecomeASubstituteUntilThePlacementEnds() {
        World w = newWorld();

        Resp resp = put("/api/v1/teachers/" + w.teacherA().teacherId() + "/engagement", w.admin(),
                Map.of("engagement", "SUBSTITUTE",
                        "version", jdbc.queryForObject("select version from teacher where id = ?", Long.class, w.teacherA().teacherId())));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("placed");
    }
}
