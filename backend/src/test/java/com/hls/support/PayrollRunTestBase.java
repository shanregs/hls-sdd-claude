package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
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

    // ------------------------------------------------------------------ the run endpoints (US1)

    protected static final String RUNS = "/api/v1/payroll/runs";

    /** A Teacher's attendance month locked, written the way the attendance tests write it. */
    protected void lockTeacherMonth(UUID teacher) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 20, 0, 0, 0, 0, 20, now(), 0)",
                UUID.randomUUID(),
                teacher,
                month.toString());
    }

    /** A Manager who worked every default-calendar working day of the month, locked through the real endpoint. */
    protected void lockWorkedManagerMonth(String admin, UUID managerId) {
        for (LocalDate date : workingDays(null)) {
            managerCheckIn(managerId, date);
        }
        Resp locked = post("/api/v1/manager-attendance/months/" + month + "/lock", admin, Map.of("managerId", managerId.toString()));
        assertThat(locked.status()).as(locked.body()).isEqualTo(200);
    }

    protected Resp startRun(String token) {
        return post(RUNS, token, Map.of("month", month.toString()));
    }

    /** Polls the run until its computation is DONE (or FAILED) and returns the run. */
    protected Map<String, Object> awaitComputed(String token, UUID runId) {
        long deadline = System.currentTimeMillis() + 180_000;
        while (true) {
            Resp resp = get(RUNS + "/" + runId, token);
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            Map<String, Object> run = resp.map();
            String state = (String) run.get("computeState");
            if ("DONE".equals(state) || "FAILED".equals(state)) {
                return run;
            }
            assertThat(System.currentTimeMillis()).as("the run did not finish: " + run).isLessThan(deadline);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /** Starts the run for the month and waits for it; returns the run id. */
    protected UUID startAndCompute(String token) {
        Resp started = startRun(token);
        assertThat(started.status()).as(started.body()).isEqualTo(202);
        UUID id = started.id();
        Map<String, Object> done = awaitComputed(token, id);
        assertThat(done.get("computeState")).as(done.toString()).isEqualTo("DONE");
        return id;
    }

    /** Every line of the run, all pages. */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> allLines(String token, UUID runId) {
        List<Map<String, Object>> all = new java.util.ArrayList<>();
        for (int page = 0; ; page++) {
            Resp resp = get(RUNS + "/" + runId + "/lines?size=200&page=" + page, token);
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            List<Map<String, Object>> content = content(resp);
            all.addAll(content);
            if (all.size() >= total(resp) || content.isEmpty()) {
                return all;
            }
        }
    }

    /** The person's line in the run, or null when they are not in it. */
    protected Map<String, Object> lineOf(String token, UUID runId, UUID personId) {
        return allLines(token, runId).stream()
                .filter(l -> personId.toString().equals(l.get("personId")))
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------- review and approval (US2)

    /** A computed draft with two paid Teachers (26,000 and 31,200 a month) whose attendance month is locked. */
    public record Draft(UUID run, UUID teacher, UUID otherTeacher, Site site) {}

    protected Draft readyDraft(String admin) {
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);
        UUID other = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, other, "31200", LocalDate.now().minusDays(300));
        lockTeacherMonth(other);
        return new Draft(startAndCompute(admin), teacher, other, site);
    }

    protected Resp submitRun(String token, UUID run) {
        return post(RUNS + "/" + run + "/submit", token, Map.of());
    }

    protected Resp approveRun(String token, UUID run) {
        return post(RUNS + "/" + run + "/approve", token, Map.of());
    }

    protected Resp returnRun(String token, UUID run, String reason) {
        return post(RUNS + "/" + run + "/return", token, Map.of("reason", reason));
    }

    /** Submits as the Admin and approves as a Director; returns the Director's token. */
    protected String submitAndApprove(String admin, UUID run) {
        String director = signInAs(Role.DIRECTOR).token();
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        Resp approved = approveRun(director, run);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        return director;
    }

    protected Map<String, Object> runOf(String token, UUID run) {
        return get(RUNS + "/" + run, token).map();
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
