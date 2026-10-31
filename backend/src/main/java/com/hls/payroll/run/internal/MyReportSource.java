package com.hls.payroll.run.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.RollupView;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayrollFigures;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportSource;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * My Report (spec 014a US4): a Teacher own attendance and leave for the six months ending with the chosen one, and their own
 * payslip history. It offers no filter, so there is no way to ask for another Teacher: the Teacher is found from the signed-in
 * user only. A Manager reads their Teachers through the Attendance report scope.
 */
@Component
class MyReportSource implements ReportSource {

    static final String KEY = "my";
    private static final int MONTHS = 6;

    private static final List<Column> ATTENDANCE_COLUMNS = List.of(
            new Column("workingDays", "Working days", Unit.DAYS),
            new Column("present", "Present", Unit.DAYS),
            new Column("leave", "Leave", Unit.DAYS),
            new Column("absent", "Absent", Unit.DAYS),
            new Column("percent", "Attendance %", Unit.PERCENT));

    private static final List<Column> PAYSLIP_COLUMNS = List.of(
            new Column("state", "Run state", Unit.TEXT),
            new Column("gross", "Gross", Unit.RUPEES),
            new Column("lop", "LOP deduction", Unit.RUPEES),
            new Column("reimbursements", "Reimbursements", Unit.RUPEES),
            new Column("net", "Net", Unit.RUPEES));

    private final TeacherDirectory teachers;
    private final AttendanceReadApi attendance;
    private final PayrollFigures figures;

    MyReportSource(TeacherDirectory teachers, AttendanceReadApi attendance, PayrollFigures figures) {
        this.teachers = teachers;
        this.attendance = attendance;
        this.figures = figures;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(KEY, "My report", PermissionModule.MY_REPORT, Set.of(), false, false);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("attendance", "My attendance and leave", ATTENDANCE_COLUMNS, this::attendanceSection),
                new Part("payslips", "My payslips", PAYSLIP_COLUMNS, this::payslipSection));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    private Optional<UUID> self(ReportContext ctx) {
        return teachers.teacherOfUser(ctx.userId()).map(TeacherDirectory.TeacherInfo::id);
    }

    private Section attendanceSection(ReportContext ctx) {
        Optional<UUID> teacher = self(ctx);
        if (teacher.isEmpty()) {
            return Section.of("attendance", "My attendance and leave", ATTENDANCE_COLUMNS, List.of(), null);
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            YearMonth m = ctx.month().minusMonths(i);
            RollupView r = attendance.rollupOf(teacher.get(), m);
            BigDecimal absent = r.workingDays().subtract(r.daysWorked()).subtract(r.daysLeave()).max(BigDecimal.ZERO);
            Cell percent = r.workingDays().signum() <= 0
                    ? Cell.noData()
                    : Cell.of(r.daysWorked().multiply(BigDecimal.valueOf(100)).divide(r.workingDays(), 1, RoundingMode.HALF_UP));
            rows.add(new Row(
                    m.toString(), m.toString(),
                    List.of(Cell.of(r.workingDays()), Cell.of(r.daysWorked()), Cell.of(r.daysLeave()), Cell.of(absent), percent)));
        }
        return Section.of("attendance", "My attendance and leave", ATTENDANCE_COLUMNS, rows, null);
    }

    private Section payslipSection(ReportContext ctx) {
        Optional<UUID> teacher = self(ctx);
        if (teacher.isEmpty()) {
            return Section.of("payslips", "My payslips", PAYSLIP_COLUMNS, List.of(), null);
        }
        List<Row> rows = new ArrayList<>();
        for (PayrollFigures.OwnMonth m : figures.historyOf(Kind.TEACHER, teacher.get())) {
            rows.add(new Row(
                    m.month().toString(),
                    m.month().toString(),
                    m.payable()
                            ? List.of(Cell.of(m.state().name()), Cell.of(m.gross()), Cell.of(m.lossOfPay()), Cell.of(m.reimbursements()), Cell.of(m.net()))
                            : List.of(Cell.of(m.state().name()), Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData())));
        }
        return Section.of("payslips", "My payslips", PAYSLIP_COLUMNS, rows, null);
    }
}
