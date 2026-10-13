package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

/**
 * Helpers for the spec 013b tests: a Teacher, Manager or staff member with a salary and attendance in the previous
 * business month, written the way the earlier specs' tests write them (salaries in the past go in by SQL because the
 * endpoints refuse them by design).
 */
public abstract class PayrollRunTestBase extends PayrollTestBase {

    protected YearMonth month;

    public record Site(UUID schoolId, String managerToken) {}

    @BeforeEach
    void previousBusinessMonth() {
        month = YearMonth.from(today()).minusMonths(1);
    }

    /**
     * TRUNCATE does not fire row triggers, so a test can start clean although approved rows are otherwise final. The
     * database is shared by every test class, so run tests clear the run tables before they start.
     */
    protected void resetRuns() {
        jdbc.execute("truncate payroll_paid_month, payroll_payment_entry, payroll_payslip, payroll_run_event,"
                + " payroll_run_line_adjustment, payroll_adjustment, payroll_run_line, payroll_run");
    }

    protected Site site(String admin) {
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        return new Site(ids[2], manager.token());
    }

    protected UUID teacherAt(String admin, UUID schoolId, LocalDate from) {
        UUID teacher = teacher(admin);
        Resp placed = placeTeacherRaw(admin, teacher, schoolId, from);
        assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        return teacher;
    }

    protected void teacherSalary(String admin, UUID teacher, String amount, LocalDate effectiveOn) {
        Resp resp = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal(amount), "effectiveOn", effectiveOn.toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    protected void mark(Site site, UUID teacher, LocalDate date, String code, double value) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + date,
                site.managerToken(),
                Map.of("statusCode", code, "dayValue", value));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    protected void personSalaryInThePast(String kind, UUID personId, String amount, LocalDate effectiveOn) {
        jdbc.update(
                "insert into person_salary (id, person_kind, person_id, amount, effective_on, note, recorded_by, recorded_at)"
                        + " values (?, ?, ?, ?::numeric, ?, null, ?, ?)",
                UUID.randomUUID(),
                kind,
                personId,
                amount,
                effectiveOn,
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));
    }

    /** A Manager who joined well before the month, with a recorded salary when {@code salary} is not null. */
    protected ManagerCtx paidManager(String admin, String salary) {
        ManagerCtx manager = newManager(admin);
        assertThat(employment(admin, manager.managerId(), uniqueEmployeeId(), month.atDay(1).minusMonths(3), null).status())
                .isEqualTo(200);
        if (salary != null) {
            personSalaryInThePast("MANAGER", manager.managerId(), salary, LocalDate.now().minusDays(400));
        }
        return manager;
    }

    /** A check-in for a Manager on a date, written directly. */
    protected void managerCheckIn(UUID managerId, LocalDate date) {
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, day_type, check_in_at, check_in_lat, check_in_lng, version)"
                        + " values (?, ?, ?, 'OFFICE', ?, 12.97, 77.59, 0)",
                UUID.randomUUID(),
                managerId,
                date,
                Timestamp.from(date.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()));
    }

    protected List<LocalDate> workingDays(UUID schoolIdOrNull) {
        return workingCalendar.workingDays(schoolIdOrNull, month.atDay(1), month.atEndOfMonth());
    }

    protected static BigDecimal perDay(String salary, int workingDays) {
        return new BigDecimal(salary).divide(BigDecimal.valueOf(workingDays), 10, RoundingMode.HALF_EVEN);
    }

    protected static PersonRef teacherRef(UUID id) {
        return new PersonRef(Kind.TEACHER, id);
    }

    protected static PersonRef managerRef(UUID id) {
        return new PersonRef(Kind.MANAGER, id);
    }

    protected static PersonRef staffRef(UUID userId) {
        return new PersonRef(Kind.STAFF, userId);
    }
}
