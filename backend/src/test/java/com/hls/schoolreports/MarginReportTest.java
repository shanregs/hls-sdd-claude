package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayrollFigures;
import com.hls.payroll.api.PayrollFigures.PersonFigures;
import com.hls.payroll.api.PayrollFigures.RunState;
import com.hls.payroll.api.PayrollFigures.RunSummary;
import com.hls.schoolbilling.ReceivablesTestBase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec 014b US1: the Margin report is the 022 fee less the 013b payslip net per Teacher and School; a fee with no payable
 * payslip (or the reverse) is "not matched" and left out of totals; a draft run is labelled; no run is "no data yet"; and a
 * Zone Manager sees only their Schools' lines. The pay side is stubbed on {@link PayrollFigures} (its own figures are tested
 * with payroll), so the arithmetic and the matching rules are checked exactly.
 */
class MarginReportTest extends ReceivablesTestBase {

    private static final String REPORT = "/api/v1/reports/margin";

    @MockitoSpyBean
    private PayrollFigures payroll;

    @AfterEach
    void restore() {
        reset(payroll);
    }

    private static PersonFigures slip(UUID teacher, UUID zone, UUID school, boolean payable, String net) {
        BigDecimal amount = net == null ? null : new BigDecimal(net);
        return new PersonFigures(teacher, Kind.TEACHER, "Slip " + teacher, zone, school, payable, amount, amount == null ? null : BigDecimal.ZERO,
                amount == null ? null : BigDecimal.ZERO, amount);
    }

    private void runIs(RunState state, PersonFigures... people) {
        YearMonth month = thisMonth();
        BigDecimal net = BigDecimal.ZERO;
        RunSummary summary = new RunSummary(UUID.randomUUID(), month, state, net, net, net, net, people.length, 0);
        doReturn(Optional.of(summary)).when(payroll).runOf(month);
        doReturn(List.of(people)).when(payroll).peopleOf(month);
    }

    private void noRun() {
        doReturn(Optional.empty()).when(payroll).runOf(thisMonth());
        doReturn(List.of()).when(payroll).peopleOf(thisMonth());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String key) {
        return (List<Map<String, Object>>) section(resp, key).get("rows");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cell(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index);
    }

    private static Map<String, Object> rowOf(List<Map<String, Object>> rows, UUID teacher) {
        return rows.stream().filter(r -> ((String) r.get("id")).startsWith(teacher.toString())).findFirst().orElseThrow();
    }

    private static BigDecimal value(Map<String, Object> cell) {
        assertThat(cell.get("state")).as(cell.toString()).isEqualTo("VALUE");
        return new BigDecimal(String.valueOf(cell.get("value")));
    }

    private Resp margin(String token) {
        Resp resp = get(REPORT + "?month=" + thisMonth(), token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp;
    }

    // columns of the teachers section: school, zone, days, fee, pay, margin, status
    private static final int FEE = 3;
    private static final int PAY = 4;
    private static final int MARGIN = 5;
    private static final int STATUS = 6;

    @Test
    void feeIsTheDueLinePayIsThePayslipNetAndMarginIsTheDifference() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000", "17000");
        UUID a = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID b = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        UUID c = mapTeacher(admin, f, 2, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        runIs(RunState.APPROVED, slip(a, f.zoneId(), f.schoolId(), true, "9000.00"), slip(b, f.zoneId(), f.schoolId(), true, "10000.50"),
                slip(c, f.zoneId(), f.schoolId(), true, "11000.00"));

        Resp resp = margin(admin);
        List<Map<String, Object>> teachers = rows(resp, "teachers");

        for (Map.Entry<UUID, String> e : Map.of(a, "9000.00", b, "10000.50", c, "11000.00").entrySet()) {
            Map<String, Object> row = rowOf(teachers, e.getKey());
            BigDecimal fee = value(cell(row, FEE));
            BigDecimal pay = value(cell(row, PAY));
            assertThat(pay).isEqualByComparingTo(e.getValue());
            assertThat(value(cell(row, MARGIN))).isEqualByComparingTo(fee.subtract(pay));
            assertThat(cell(row, STATUS).get("value")).isEqualTo("matched");
        }
        // the School total is the sum of the three matched rows
        Map<String, Object> schoolRow = rows(resp, "schools").stream()
                .filter(r -> f.schoolId().toString().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(value(cell(schoolRow, 1))).isEqualByComparingTo("3");
        assertThat(value(cell(schoolRow, 2))).isEqualByComparingTo("48000.00");
        assertThat(value(cell(schoolRow, 3))).isEqualByComparingTo("30000.50");
        assertThat(value(cell(schoolRow, 4))).isEqualByComparingTo("17999.50");
        assertThat(resp.map().get("monthState")).isEqualTo("APPROVED");
    }

    @Test
    void aDueWithoutAPayslipAndAPayslipWithoutADueAreNotMatchedAndLeftOutOfTotals() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000", "16000", "17000");
        UUID matched = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID noSlip = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        UUID cannotPay = mapTeacher(admin, f, 2, thisMonth().atDay(1));
        UUID noDue = teacher(admin);
        raiseOne(admin, thisMonth(), f.schoolId());
        runIs(RunState.APPROVED, slip(matched, f.zoneId(), f.schoolId(), true, "9000.00"),
                slip(cannotPay, f.zoneId(), f.schoolId(), false, null), slip(noDue, f.zoneId(), f.schoolId(), true, "7000.00"));

        Resp resp = margin(admin);
        List<Map<String, Object>> teachers = rows(resp, "teachers");

        Map<String, Object> missingSlip = rowOf(teachers, noSlip);
        assertThat(cell(missingSlip, PAY).get("state")).isEqualTo("NOT_MATCHED");
        assertThat(cell(missingSlip, MARGIN).get("state")).isEqualTo("NOT_MATCHED");
        assertThat((String) cell(missingSlip, STATUS).get("value")).contains("no payslip");
        assertThat((String) cell(rowOf(teachers, cannotPay), STATUS).get("value")).contains("cannot be paid");
        Map<String, Object> missingDue = rowOf(teachers, noDue);
        assertThat(cell(missingDue, FEE).get("state")).isEqualTo("NOT_MATCHED");
        assertThat(value(cell(missingDue, PAY))).isEqualByComparingTo("7000.00");
        assertThat((String) cell(missingDue, STATUS).get("value")).contains("no School fee");
        // only the matched Teacher is in the totals
        Map<String, Object> schoolRow = rows(resp, "schools").stream()
                .filter(r -> f.schoolId().toString().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(value(cell(schoolRow, 1))).isEqualByComparingTo("1");
        assertThat(value(cell(schoolRow, 3))).isEqualByComparingTo("9000.00");
        assertThat(value(cell(schoolRow, 2))).isEqualByComparingTo(value(cell(rowOf(teachers, matched), FEE)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDraftRunIsLabelledAndNoRunIsNoDataYetNotNotMatched() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID t = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());

        runIs(RunState.DRAFT, slip(t, f.zoneId(), f.schoolId(), true, "9000.00"));
        Resp draft = margin(admin);
        assertThat(draft.map().get("monthState")).isEqualTo("DRAFT");
        assertThat(draft.map().get("notes").toString()).contains("not approved, figures may change");

        noRun();
        Resp none = margin(admin);
        Map<String, Object> row = rowOf(rows(none, "teachers"), t);
        assertThat(cell(row, FEE).get("state")).isEqualTo("VALUE");
        assertThat(cell(row, PAY).get("state")).isEqualTo("NO_DATA");
        assertThat(cell(row, MARGIN).get("state")).isEqualTo("NO_DATA");
        Map<String, Object> totals = (Map<String, Object>) section(none, "schools").get("totals");
        assertThat(cell(totals, 2).get("state")).isEqualTo("VALUE");
        assertThat(cell(totals, 3).get("state")).isEqualTo("NO_DATA");
    }

    @Test
    void aNegativeMarginIsShownAsNegative() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID t = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        runIs(RunState.APPROVED, slip(t, f.zoneId(), f.schoolId(), true, "99999.00"));

        Map<String, Object> row = rowOf(rows(margin(admin), "teachers"), t);

        assertThat(value(cell(row, MARGIN)).signum()).isNegative();
    }

    @Test
    void aZoneManagerSeesOnlyTheirSchoolsAndCannotOpenThePayrollReport() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = billedSchool(admin, "15000");
        Fixture theirs = billedSchool(admin, "16000");
        UUID mineTeacher = mapTeacher(admin, mine, 0, thisMonth().atDay(1));
        UUID theirTeacher = mapTeacher(admin, theirs, 0, thisMonth().atDay(1));
        raise(admin, thisMonth(), mine.schoolId(), theirs.schoolId());
        runIs(RunState.APPROVED, slip(mineTeacher, mine.zoneId(), mine.schoolId(), true, "9000.00"),
                slip(theirTeacher, theirs.zoneId(), theirs.schoolId(), true, "9500.00"));

        String manager = mine.manager().token();
        Resp resp = margin(manager);

        List<Map<String, Object>> teachers = rows(resp, "teachers");
        assertThat(teachers).hasSize(1);
        assertThat(rowOf(teachers, mineTeacher)).isNotNull();
        assertThat(resp.body()).doesNotContain(theirTeacher.toString());
        assertThat(get("/api/v1/reports/payroll?month=" + thisMonth(), manager).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + thisMonth() + "&zoneId=" + theirs.zoneId(), manager).status()).isIn(403, 404);
    }

    @Test
    void aZoneManagerWithNoSchoolsSeesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID t = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        runIs(RunState.APPROVED, slip(t, f.zoneId(), f.schoolId(), true, "9000.00"));
        String lonely = newManager(admin).token();

        Resp resp = margin(lonely);

        assertThat(rows(resp, "teachers")).isEmpty();
        assertThat(rows(resp, "schools")).isEmpty();
        assertThat(resp.body()).doesNotContain(t.toString());
    }
}
