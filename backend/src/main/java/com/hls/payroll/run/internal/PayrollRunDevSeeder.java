package com.hls.payroll.run.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.internal.PersonDirectory;
import com.hls.payroll.internal.PersonDirectory.TeacherSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data (inert unless {@code hls.seed.demo-data=true}, idempotent, ordered after the attendance, leave and
 * salary seeders): locks last month for the Teachers who have a salary and have it open, then leaves a computed draft
 * pay run for last month so the Payroll screens have something to show. The run is started through the real
 * {@link RunService}, so the real batch computes it; a month that already has a run is never touched.
 *
 * <p>The Manager month is not forced: a Manager whose month is not locked shows on the run as "cannot be paid" with the
 * reason, which is the other thing the screens should be tried against.
 */
@Component
@Order(80)
@ConditionalOnProperty(name = "hls.seed.demo-data", havingValue = "true")
class PayrollRunDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PayrollRunDevSeeder.class);

    private final RunService runService;
    private final PayRunRepository runs;
    private final PersonDirectory people;
    private final AppUserRepository users;
    private final WorkingCalendar calendar;
    private final JdbcTemplate jdbc;

    PayrollRunDevSeeder(
            RunService runService,
            PayRunRepository runs,
            PersonDirectory people,
            AppUserRepository users,
            WorkingCalendar calendar,
            JdbcTemplate jdbc) {
        this.runService = runService;
        this.runs = runs;
        this.people = people;
        this.users = users;
        this.calendar = calendar;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByPhone("9800000001").orElse(null);
        if (admin == null) {
            return;
        }
        LocalDate today = calendar.businessToday();
        YearMonth last = YearMonth.from(today).minusMonths(1);
        if (runs.findByMonthOrderByKindAscSequenceAsc(last.atDay(1)).isEmpty()) {
            int locked = lockTeachers(last);
            runService.start(admin.getId(), last.toString(), false);
            log.info("[DEV SEED] Payroll run demo data ready: {} Teacher month(s) locked, a draft run for {} started.", locked, last);
        }
        seedAwaitingApproval(admin.getId(), last.minusMonths(1));
    }

    /**
     * A second run, for the month before last, computed and sent for approval, so the Director's inbox and the dashboard have a
     * pay run awaiting a decision (spec 028). Waits briefly for the background computation; if it is not done the run stays a draft.
     */
    private void seedAwaitingApproval(UUID admin, YearMonth month) {
        if (!runs.findByMonthOrderByKindAscSequenceAsc(month.atDay(1)).isEmpty()) {
            return;
        }
        lockTeachers(month);
        UUID runId = runService.start(admin, month.toString(), false).id();
        for (int i = 0; i < 60; i++) {
            PayRun run = runs.findById(runId).orElse(null);
            if (run != null && run.getComputeState() == PayRun.ComputeState.DONE) {
                try {
                    runService.submit(admin, runId);
                    log.info("[DEV SEED] Payroll run for {} is awaiting approval.", month);
                } catch (RuntimeException e) {
                    log.warn("[DEV SEED] The run for {} could not be sent for approval: {}", month, e.getMessage());
                }
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("[DEV SEED] The run for {} did not finish computing; it stays a draft.", month);
    }

    /** Locks the month for each Teacher with a salary whose month is still open; a month already locked is left alone. */
    private int lockTeachers(YearMonth month) {
        List<TeacherSummary> all = people.allTeachers();
        var salaries = people.teacherSalariesOn(all.stream().map(TeacherSummary::id).toList(), month.atEndOfMonth());
        int locked = 0;
        for (TeacherSummary teacher : all) {
            if (!salaries.containsKey(teacher.id())) {
                continue;
            }
            Integer existing = jdbc.queryForObject(
                    "select count(*) from attendance_teacher_month where teacher_id = ? and year_month = ?",
                    Integer.class,
                    teacher.id(),
                    month.toString());
            if (existing != null && existing > 0) {
                continue;
            }
            jdbc.update(
                    "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                            + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                            + " values (?, ?, ?, 'LOCKED', 20, 20, 0, 0, 0, 0, 20, now(), 0)",
                    UUID.randomUUID(),
                    teacher.id(),
                    month.toString());
            locked++;
        }
        return locked;
    }
}
