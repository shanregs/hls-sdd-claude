package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.SchoolWorkingDays;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 022 US1 and US2: raising a School's monthly due from its active assignments, cancelling it, who may. */
class RaiseDueApiTest extends ReceivablesTestBase {

    @Autowired
    private WorkingCalendar calendar;

    @Autowired
    private SchoolWorkingDays workingDays;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Resp detail) {
        return (List<Map<String, Object>>) detail.map().get("lines");
    }

    @Test
    void teachersAssignedForTheWholeMonthAreBilledTheirPositionSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000");
        YearMonth month = thisMonth();
        mapTeacher(admin, f, 0, month.atDay(1));
        mapTeacher(admin, f, 1, month.atDay(1));

        UUID due = raiseOne(admin, month, f.schoolId());

        Map<String, Object> row = dueOf(admin, due);
        assertThat(dec(row.get("total"))).isEqualByComparingTo("31000.00");
        assertThat(dec(row.get("balance"))).isEqualByComparingTo("31000.00");
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat((String) row.get("reference")).matches("MD-\\d{6}-\\d{4}");
        assertThat(lines(dueDetail(admin, due))).hasSize(2).allMatch(l -> (Boolean) l.get("fullMonth"));
    }

    @Test
    void aTeacherWhoJoinedMidMonthIsBilledForTheirWorkingDaysAtTheSchool() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000");
        YearMonth month = thisMonth();
        mapTeacher(admin, f, 0, month.atDay(1));
        LocalDate joined = month.atDay(11);
        UUID late = mapTeacher(admin, f, 1, joined);

        UUID due = raiseOne(admin, month, f.schoolId());

        BigDecimal monthDays = workingDays.forMonth(f.schoolId(), month).workingDays();
        BigDecimal worked = BigDecimal.valueOf(calendar.workingDays(f.schoolId(), joined, month.atEndOfMonth()).size());
        BigDecimal expected = new BigDecimal("16000").multiply(worked).divide(monthDays, 2, RoundingMode.HALF_UP);
        Map<String, Object> line = lines(dueDetail(admin, due)).stream()
                .filter(l -> late.toString().equals(l.get("teacherId")))
                .findFirst()
                .orElseThrow();
        assertThat(dec(line.get("amount"))).isEqualByComparingTo(expected);
        assertThat(dec(line.get("amount"))).isLessThan(new BigDecimal("16000"));
        assertThat((Boolean) line.get("fullMonth")).isFalse();
        assertThat((String) line.get("working")).contains("working days");
        assertThat(dec(dueOf(admin, due).get("total"))).isEqualByComparingTo(new BigDecimal("15000").add(expected));
    }

    @Test
    void aTeacherTransferredBetweenSchoolsInTheMonthIsBilledToEachForItsPart() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = billedSchool(admin, "20000");
        Fixture b = billedSchool(admin, "20000");
        YearMonth month = thisMonth();
        UUID teacher = mapTeacher(admin, a, 0, month.atDay(1));
        LocalDate moved = month.atDay(16);
        assertThat(assign(admin, teacher, b.schoolId(), positionId(admin, b.schoolId(), 0), moved).status()).isEqualTo(200);

        UUID dueA = raiseOne(admin, month, a.schoolId());
        UUID dueB = raiseOne(admin, month, b.schoolId());

        BigDecimal totalA = dec(dueOf(admin, dueA).get("total"));
        BigDecimal totalB = dec(dueOf(admin, dueB).get("total"));
        assertThat(totalA).isPositive().isLessThan(new BigDecimal("20000"));
        assertThat(totalB).isPositive().isLessThan(new BigDecimal("20000"));
        // a School with the same calendar bills the two parts as one month, within a cent of rounding per line
        assertThat(totalA.add(totalB).subtract(new BigDecimal("20000")).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void aSecondRaiseForTheSameMonthIsSkippedAndShowsTheExistingDue() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());

        Resp again = raise(admin, thisMonth(), f.schoolId());

        assertThat(again.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) again.map().get("skipped");
        assertThat(skipped).hasSize(1);
        assertThat(skipped.get(0)).containsEntry("reason", "ALREADY_RAISED").containsEntry("existingDueId", due.toString());
    }

    @Test
    void aSchoolWithNoMouIsListedAsNotBilled() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), null, thisMonth().atDay(1)).status()).isEqualTo(200);

        Resp resp = raise(admin, thisMonth(), f.schoolId());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains("NOTHING_TO_BILL");
        Resp notBilled = get(BILLING + "/not-billed?month=" + thisMonth(), admin);
        assertThat(notBilled.body()).contains(teacher.toString()).contains("MOU_PENDING");
    }

    @Test
    void aMonthThatHasNotStartedCannotBeRaised() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));

        Resp resp = raise(admin, thisMonth().plusMonths(1), f.schoolId());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains("NOTHING_TO_BILL").doesNotContain("\"raised\":[{");
    }

    @Test
    void aPastMonthCanBeRaisedBackdated() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        YearMonth previous = thisMonth().minusMonths(1);
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, f.schoolId(), positionId(admin, f.schoolId(), 0), previous.atEndOfMonth().minusDays(10))
                        .status())
                .isEqualTo(200);
        // the contract starts 40 days ago, so the previous month is only partly covered: the due is raised for those days
        Resp resp = raise(admin, previous, f.schoolId());
        assertThat(resp.status()).isEqualTo(200);
    }

    @Test
    void onlyAdminAndDirectorMayRaiseAndCancel() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));

        assertThat(raise(f.manager().token(), thisMonth(), f.schoolId()).status()).isEqualTo(403);
        assertThat(raise(signInAs(Role.TEACHER).token(), thisMonth(), f.schoolId()).status()).isEqualTo(403);
        assertThat(raise(signInAs(Role.SYSTEM).token(), thisMonth(), f.schoolId()).status()).isEqualTo(403);
        UUID due = raiseOne(director().token(), thisMonth(), f.schoolId());
        assertThat(post(BILLING + "/dues/" + due + "/cancel", f.manager().token(), Map.of("reason", "wrong")).status()).isEqualTo(403);
    }

    @Test
    void cancellingNeedsAReasonKeepsTheDueVisibleAndAllowsAReplacement() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());

        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp cancelled = post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "Wrong salary on the MoU"));
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map()).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "Wrong salary on the MoU");
        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "again")).status()).isEqualTo(409);

        UUID replacement = raiseOne(admin, thisMonth(), f.schoolId());
        Resp list = get(BILLING + "/dues?schoolId=" + f.schoolId(), admin);
        assertThat(content(list)).extracting(m -> m.get("id")).contains(due.toString(), replacement.toString());
        Resp ledger = get(BILLING + "/schools/" + f.schoolId() + "/ledger", admin);
        assertThat(dec(ledger.map().get("owed"))).isEqualByComparingTo("15000.00");
    }

    @Test
    void theDueDateIsTheRaisedDatePlusThePaymentDueDaysSetting() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());

        Map<String, Object> row = dueOf(admin, due);

        assertThat(LocalDate.parse((String) row.get("dueOn")))
                .isEqualTo(LocalDate.parse((String) row.get("raisedOn")).plusDays(15));
    }

    @Test
    void aZoneManagerSeesOnlyTheirOwnSchoolsDues() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = billedSchool(admin, "15000");
        Fixture theirs = billedSchool(admin, "15000");
        mapTeacher(admin, mine, 0, thisMonth().atDay(1));
        mapTeacher(admin, theirs, 0, thisMonth().atDay(1));
        UUID myDue = raiseOne(admin, thisMonth(), mine.schoolId());
        UUID theirDue = raiseOne(admin, thisMonth(), theirs.schoolId());
        String manager = mine.manager().token();

        assertThat(dueDetail(manager, myDue).status()).isEqualTo(200);
        assertThat(dueDetail(manager, theirDue).status()).isEqualTo(404);
        assertThat(get(BILLING + "/dues?schoolId=" + theirs.schoolId(), manager).status()).isEqualTo(404);
        assertThat(get(BILLING + "/schools/" + theirs.schoolId() + "/ledger", manager).status()).isEqualTo(404);
        assertThat(content(get(BILLING + "/dues", manager))).extracting(m -> m.get("id")).contains(myDue.toString())
                .doesNotContain(theirDue.toString());
    }
}
