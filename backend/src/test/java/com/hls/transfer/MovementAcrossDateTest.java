package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.PlacementAttendance;
import com.hls.substitution.api.PlacementSubstitution;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 027 US3 (T039 to T042): attendance, leave and substitution on either side of the move date, the module ports on
 * their own, and the day-by-day invariant that nothing dated before the effective date changes.
 */
class MovementAcrossDateTest extends TransferTestBase {

    @Autowired
    private PlacementAttendance attendance;

    @Autowired
    private PlacementSubstitution substitution;

    @Autowired
    private PlatformTransactionManager transactions;

    private static final UUID ACTOR = UUID.randomUUID();

    private UUID schoolOfMark(UUID teacher, LocalDate date) {
        return jdbc.queryForObject("select school_id from attendance_mark where teacher_id = ? and mark_date = ?", UUID.class, teacher, date);
    }

    private void requirement(UUID teacher, UUID school, LocalDate date, String status, UUID substitute) {
        jdbc.update(
                "insert into substitution_requirement (id, teacher_id, school_id, duty_date, day_value, kind, status, substitute_teacher_id,"
                        + " raised_at, updated_at, version) values (?, ?, ?, ?, 1.00, 'ABSENT', ?, ?, now(), now(), 0)",
                UUID.randomUUID(), teacher, school, date, status, substitute);
    }

    // ---- the attendance port (T039)

    @Test
    void attendancePortSeesNonLeaveMarksAndLockedMonths() {
        World w = world();
        mark(w.t1(), in(5), LEAVE, w.schoolA1());
        assertThat(attendance.hasNonLeaveMarkOnOrAfter(w.t1(), in(3))).isFalse();
        mark(w.t1(), in(6), PRESENT, w.schoolA1());
        assertThat(attendance.hasNonLeaveMarkOnOrAfter(w.t1(), in(3))).isTrue();
        assertThat(attendance.hasNonLeaveMarkOnOrAfter(w.t1(), in(7))).isFalse();

        assertThat(attendance.anyMonthLocked(w.t1(), in(3))).isFalse();
        lockMonth(w.t1(), in(3));
        assertThat(attendance.anyMonthLocked(w.t1(), in(3))).isTrue();
        // a later locked month holding marks counts, an earlier one does not
        World other = world();
        mark(other.t1(), in(70), LEAVE, other.schoolA1());
        lockMonth(other.t1(), in(70));
        assertThat(attendance.anyMonthLocked(other.t1(), in(3))).isTrue();
        World earlier = world();
        lockMonth(earlier.t1(), in(-70));
        assertThat(attendance.anyMonthLocked(earlier.t1(), in(3))).isFalse();
    }

    @Test
    void retagChangesOnlyLeaveMarksFromTheDateAndWritesHistory() {
        World w = world();
        mark(w.t1(), in(1), LEAVE, w.schoolA1());
        mark(w.t1(), in(3), LEAVE, w.schoolA1());
        mark(w.t1(), in(4), LEAVE, w.schoolA1());
        int changed = new TransactionTemplate(transactions).execute(s -> attendance.retagLeaveMarks(ACTOR, w.t1(), in(3), w.schoolB1()));
        assertThat(changed).isEqualTo(2);
        assertThat(schoolOfMark(w.t1(), in(1))).isEqualTo(w.schoolA1());
        assertThat(schoolOfMark(w.t1(), in(3))).isEqualTo(w.schoolB1());
        assertThat(schoolOfMark(w.t1(), in(4))).isEqualTo(w.schoolB1());
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark_history where teacher_id = ? and action = 'CORRECTED' and school_id = ?",
                        Integer.class, w.t1(), w.schoolB1()))
                .isEqualTo(2);
    }

    @Test
    void retagRefusesALockedMonth() {
        World w = world();
        mark(w.t1(), in(3), LEAVE, w.schoolA1());
        lockMonth(w.t1(), in(3));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new TransactionTemplate(transactions).execute(s -> attendance.retagLeaveMarks(ACTOR, w.t1(), in(3), w.schoolB1())))
                .isInstanceOf(com.hls.school.api.ConflictException.class);
        assertThat(schoolOfMark(w.t1(), in(3))).isEqualTo(w.schoolA1());
    }

    // ---- the substitution port (T040)

    @Test
    void relocateMovesOpenRequirementsAndReturnsAssignedOnesToOpen() {
        World w = world();
        UUID substitute = teacher(w.admin());
        requirement(w.t1(), w.schoolA1(), in(1), "OPEN", null);
        requirement(w.t1(), w.schoolA1(), in(4), "OPEN", null);
        requirement(w.t1(), w.schoolA1(), in(5), "ASSIGNED", substitute);
        requirement(w.t1(), w.schoolA1(), in(6), "CANCELLED", null);
        int changed = new TransactionTemplate(transactions).execute(s -> substitution.relocate(ACTOR, w.t1(), in(3), w.schoolB1(), "Teacher moved"));
        assertThat(changed).isEqualTo(2);
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(1)))
                .isEqualTo(w.schoolA1());
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(4)))
                .isEqualTo(w.schoolB1());
        Map<String, Object> assigned = jdbc.queryForMap(
                "select school_id, status, substitute_teacher_id from substitution_requirement where teacher_id = ? and duty_date = ?", w.t1(), in(5));
        assertThat(assigned.get("school_id")).isEqualTo(w.schoolB1());
        assertThat(assigned.get("status")).isEqualTo("OPEN");
        assertThat(assigned.get("substitute_teacher_id")).isNull();
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(6)))
                .isEqualTo(w.schoolA1());
        assertThat(jdbc.queryForObject("select count(*) from substitution_history where action = 'RELEASED' and requirement_id in"
                        + " (select id from substitution_requirement where teacher_id = ?)", Integer.class, w.t1()))
                .isEqualTo(1);
    }

    @Test
    void relocateWithNoSchoolCancelsTheRequirements() {
        World w = world();
        requirement(w.t1(), w.schoolA1(), in(4), "OPEN", null);
        int changed = new TransactionTemplate(transactions).execute(s -> substitution.relocate(ACTOR, w.t1(), in(3), null, "Teacher moved"));
        assertThat(changed).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from substitution_requirement where teacher_id = ?", String.class, w.t1()))
                .isEqualTo("CANCELLED");
    }

    // ---- through the request (T041, T042)

    @Test
    void raiseAndApproveAreRefusedForAMarkOnOrAfterTheDateOrALockedMonth() {
        World w = world();
        mark(w.t1(), in(4), PRESENT, w.schoolA1());
        Resp marked = raiseTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        assertThat(marked.status()).isEqualTo(409);
        assertThat(marked.body()).contains("attendance marked");
        assertThat(rows(w.t1())).hasSize(1);

        World locked = world();
        lockMonth(locked.t1(), in(3));
        Resp lockedResp = raiseTransfer(locked.admin(), locked.t1(), locked.schoolB1(), in(3));
        assertThat(lockedResp.status()).isEqualTo(409);
        assertThat(lockedResp.body()).contains("locked");
    }

    @Test
    void approvalCarriesFutureLeaveAndSubstitutionAndLeavesEarlierDaysAlone() {
        World w = world();
        mark(w.t1(), in(1), LEAVE, w.schoolA1());
        mark(w.t1(), in(4), LEAVE, w.schoolA1());
        requirement(w.t1(), w.schoolA1(), in(1), "OPEN", null);
        requirement(w.t1(), w.schoolA1(), in(4), "OPEN", null);
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        assertThat(schoolOfMark(w.t1(), in(1))).isEqualTo(w.schoolA1());
        assertThat(schoolOfMark(w.t1(), in(4))).isEqualTo(w.schoolB1());
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(1)))
                .isEqualTo(w.schoolA1());
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(4)))
                .isEqualTo(w.schoolB1());
        // cancelling the future approved move gives the future days back to the leaving School
        assertThat(cancel(w.managerA().token(), request, "Plan changed").status()).isEqualTo(200);
        assertThat(schoolOfMark(w.t1(), in(4))).isEqualTo(w.schoolA1());
        assertThat(jdbc.queryForObject("select school_id from substitution_requirement where teacher_id = ? and duty_date = ?", UUID.class, w.t1(), in(4)))
                .isEqualTo(w.schoolA1());
    }

    @Test
    void anOutgoingTeacherWithNoDestinationKeepsLeaveDaysAtTheOldSchoolAndLosesOpenRequirements() {
        World w = world();
        mark(w.t1(), in(4), LEAVE, w.schoolA1());
        requirement(w.t1(), w.schoolA1(), in(4), "OPEN", null);
        UUID request = newReplacement(w.admin(), w.t1(), w.t3(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        assertThat(schoolOfMark(w.t1(), in(4))).isEqualTo(w.schoolA1());
        assertThat(jdbc.queryForObject("select status from substitution_requirement where teacher_id = ?", String.class, w.t1()))
                .isEqualTo("CANCELLED");
    }

    @Test
    void aFailureInAnyStepRollsBackThePlacementChangeToo() {
        World w = world();
        mark(w.t1(), in(4), LEAVE, w.schoolA1());
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        // the month locks after the request was raised, so retagging the leave mark fails inside approve; the check list
        // sees the lock first, so remove the mark-time lock check by locking only the month of the leave mark
        lockMonth(w.t1(), in(4));
        Resp refused = approve(w.director(), request);
        assertThat(refused.status()).isEqualTo(409);
        List<Map<String, Object>> rows = rows(w.t1());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("ends_on")).isNull();
        assertThat(schoolOfMark(w.t1(), in(4))).isEqualTo(w.schoolA1());
    }

    @Test
    void paymentStyleReadShowsTwoSpansMeetingOnTheDate() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        List<Map<String, Object>> rows = rows(w.t1());
        assertThat(rows).hasSize(2);
        LocalDate firstEnds = (LocalDate) ((java.sql.Date) rows.get(0).get("ends_on")).toLocalDate();
        LocalDate secondStarts = ((java.sql.Date) rows.get(1).get("starts_on")).toLocalDate();
        assertThat(firstEnds.plusDays(1)).isEqualTo(secondStarts);
        assertThat(secondStarts).isEqualTo(in(3));
    }
}
