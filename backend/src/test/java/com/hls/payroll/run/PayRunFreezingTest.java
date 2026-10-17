package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.files.api.FileStore;
import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules;
import com.hls.support.MasterDataTestBase.ManagerCtx;
import com.hls.support.PayrollRunTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec 013b T030 (SC-003, US3): once approved, nothing the rest of the system does changes the run. The mutations here
 * are the ones the spec names: a holiday, a reopened and changed Teacher month, a reopened Manager month, a backdated
 * salary and a changed pay policy. The CSV of the run is part of the picture (T055).
 */
class PayRunFreezingTest extends PayrollRunTestBase {

    @MockitoSpyBean
    private PayRules payRules;

    @MockitoSpyBean
    private AttendanceReadApi attendance;

    @MockitoSpyBean
    private ManagerAttendanceReadApi managerAttendance;

    @MockitoSpyBean
    private WorkingCalendar calendar;

    @Autowired
    private FileStore files;

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** Everything an approved run shows, read from the endpoints and the stored payslip. */
    private record Picture(
            List<Map<String, Object>> lines,
            Map<String, Object> teacherLine,
            Map<String, Object> managerLine,
            Object totals,
            Object counts,
            byte[] teacherPayslip,
            String csv) {}

    private Picture pictureOf(String token, UUID run, UUID teacher, UUID manager) {
        Map<String, Object> header = runOf(token, run);
        List<Map<String, Object>> lines = allLines(token, run);
        UUID teacherLine = UUID.fromString((String) lineOf(token, run, teacher).get("id"));
        UUID managerLine = UUID.fromString((String) lineOf(token, run, manager).get("id"));
        UUID fileId = jdbc.queryForObject("select file_id from payroll_payslip where line_id = ?", UUID.class, teacherLine);
        return new Picture(
                lines,
                get(RUNS + "/" + run + "/lines/" + teacherLine, token).map(),
                get(RUNS + "/" + run + "/lines/" + managerLine, token).map(),
                header.get("totals"),
                List.of(header.get("payableCount"), header.get("cannotPayCount")),
                files.open(fileId).orElseThrow().bytes(),
                get(RUNS + "/" + run + "/export", token).body());
    }

    @Test
    void nothingAfterApprovalChangesTheLinesTheWorkingTheTotalsOrThePayslip() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        List<LocalDate> schoolDays = workingDays(site.schoolId());
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        mark(site, teacher, schoolDays.get(2), "A", 1);
        lockTeacherMonth(teacher);
        ManagerCtx manager = paidManager(admin, "30000");
        lockWorkedManagerMonth(admin, manager.managerId());
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);
        Picture before = pictureOf(admin, run, teacher, manager.managerId());
        assertThat(before.teacherLine().get("deduction")).isNotEqualTo("0.00");

        LocalDate holiday = workingDays(null).get(4);
        try {
            // a holiday in the month changes every divisor of every later computation
            jdbc.update(
                    "insert into attendance_non_working_date (id, on_date, description, created_by) values (?, ?, 'Freezing test holiday', ?)",
                    UUID.randomUUID(),
                    holiday,
                    UUID.randomUUID());
            // a Teacher month reopened and a day changed
            assertThat(post("/api/v1/attendance/teachers/" + teacher + "/months/" + month + "/reopen", admin, Map.of("reason", "Test"))
                            .status())
                    .isEqualTo(200);
            mark(site, teacher, schoolDays.get(5), "A", 1);
            // a Manager month reopened
            assertThat(post(
                                    "/api/v1/manager-attendance/months/" + month + "/reopen",
                                    admin,
                                    Map.of("managerId", manager.managerId().toString(), "reason", "Test"))
                            .status())
                    .isEqualTo(200);
            // a salary recorded for the whole month after the fact
            teacherSalary(admin, teacher, "40000", month.atDay(1));
            // a pay policy that applies to the month's last day and rounds the other way
            jdbc.update(
                    "insert into pay_policy (id, effective_on, lop_divisor, half_day_fraction, rounding, pay_month, note, recorded_by, recorded_at)"
                            + " values (?, ?, 'WORKING_DAYS', 0.250, 'UP', 'CALENDAR_MONTH', 'freezing test', null, ?)",
                    UUID.randomUUID(),
                    month.atDay(1),
                    Timestamp.from(Instant.now()));

            Picture after = pictureOf(admin, run, teacher, manager.managerId());

            assertThat(after.lines()).isEqualTo(before.lines());
            assertThat(after.teacherLine()).isEqualTo(before.teacherLine());
            assertThat(after.managerLine()).isEqualTo(before.managerLine());
            assertThat(after.totals()).isEqualTo(before.totals());
            assertThat(after.counts()).isEqualTo(before.counts());
            assertThat(after.teacherPayslip()).as("payslip bytes").isEqualTo(before.teacherPayslip());
            assertThat(after.csv()).as("csv rows").isEqualTo(before.csv());
        } finally {
            jdbc.update("delete from attendance_non_working_date where description = 'Freezing test holiday'");
            jdbc.execute("alter table pay_policy disable trigger user");
            try {
                jdbc.update("delete from pay_policy where note = 'freezing test'");
            } finally {
                jdbc.execute("alter table pay_policy enable trigger user");
            }
        }
    }

    @Test
    void anApprovedRunsScreenDataIsBuiltWithoutAskingThePayRulesAttendanceOrTheCalendar() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        submitAndApprove(admin, draft.run());
        UUID line = UUID.fromString((String) lineOf(admin, draft.run(), draft.teacher()).get("id"));
        clearInvocations(payRules, attendance, managerAttendance, calendar);

        assertThat(get(RUNS + "/" + draft.run(), admin).status()).isEqualTo(200);
        assertThat(get(RUNS + "/" + draft.run() + "/lines?size=50", admin).status()).isEqualTo(200);
        Resp detail = get(RUNS + "/" + draft.run() + "/lines/" + line, admin);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).contains("salaryRows").contains("payableDates");

        verifyNoInteractions(payRules, attendance, managerAttendance);
        verify(calendar, never()).workingDays(any(), any(), any());
    }

    @Test
    void theNextRunForThePersonIsUnaffectedByTheFirstRunsData() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        submitAndApprove(admin, draft.run());

        // the month before is a separate run with its own computation; the approved month blocks nothing
        Resp earlier = post(RUNS, admin, Map.of("month", month.minusMonths(1).toString()));
        assertThat(earlier.status()).as(earlier.body()).isEqualTo(202);
        awaitComputed(admin, earlier.id());
        Map<String, Object> line = lineOf(admin, earlier.id(), draft.teacher());
        assertThat(line).isNotNull();
        assertThat((String) line.get("reason")).contains("not locked");
        assertThat(runOf(admin, draft.run()).get("status")).isEqualTo("APPROVED");
    }
}
