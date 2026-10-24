package com.hls.payroll.run.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.identity.permissions.PermissionAction;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three Payroll tiles (spec 028 US5). Organization-wide, granted by Payroll View, and amounts carry the RUPEES unit. A month
 * with no run is "no run yet" (an OK text), never an amount of 0.
 */
@Component
class PayrollTiles implements TileSource {

    private static final String RUN = "payroll.run";
    private static final String PAID_OWED = "payroll.paidOwed";
    private static final String LOCKS = "payroll.locks";

    private final RunService runs;
    private final OwedService owed;
    private final AttendanceReadApi teacherAttendance;
    private final ManagerAttendanceReadApi managerAttendance;
    private final TeacherDirectory teachers;
    private final ManagerQueries managers;

    PayrollTiles(
            RunService runs,
            OwedService owed,
            AttendanceReadApi teacherAttendance,
            ManagerAttendanceReadApi managerAttendance,
            TeacherDirectory teachers,
            ManagerQueries managers) {
        this.runs = runs;
        this.owed = owed;
        this.teacherAttendance = teacherAttendance;
        this.managerAttendance = managerAttendance;
        this.teachers = teachers;
        this.managers = managers;
    }

    private static TileDefinition def(String key, int order, String label, Link link) {
        return new TileDefinition(
                key, Area.PAYROLL, order, label, PeriodKind.MONTH, "PAYROLL", PermissionAction.VIEW,
                ScopeKind.ORG_WIDE_ONLY, false, link);
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                def(RUN, 1, "This month's pay run", Link.to("/operations/payroll")),
                def(PAID_OWED, 2, "Paid and owed", Link.to("/operations/payroll")),
                def(LOCKS, 3, "Attendance locked", Link.to("/operations/payroll")));
    }

    /** The month's run: the latest one that is not cancelled, else the latest. Null when there is none. */
    private RunViews.RunView runOf(TileContext ctx) {
        List<RunViews.RunView> found = ctx.memo("payroll.runs", () ->
                runs.list(ctx.month().toString(), null, 0, 50).getContent());
        RunViews.RunView latest = null;
        for (RunViews.RunView r : found) {
            if (latest == null || r.status() != com.hls.payroll.run.internal.PayRun.Status.CANCELLED || latest.status() == com.hls.payroll.run.internal.PayRun.Status.CANCELLED) {
                latest = r;
            }
        }
        return latest;
    }

    @Override
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        return switch (tile.key()) {
            case RUN -> run(tile, ctx);
            case PAID_OWED -> paidOwed(tile, ctx);
            case LOCKS -> locks(tile, ctx);
            default -> throw new IllegalArgumentException("Unknown tile " + tile.key());
        };
    }

    private TileResult run(TileDefinition tile, TileContext ctx) {
        RunViews.RunView run = runOf(ctx);
        if (run == null) {
            return TileResult.ok(RUN, Value.text("State", "No run yet", tile.link()));
        }
        Link link = Link.to("/operations/payroll/" + run.id());
        String net = run.totals() == null ? null : run.totals().net();
        if (net == null) {
            return TileResult.ok(RUN, Value.text("State", run.status().name(), link));
        }
        return TileResult.ok(RUN,
                Value.text("State", run.status().name(), link),
                Value.rupees("Total net", new BigDecimal(net), link));
    }

    private TileResult paidOwed(TileDefinition tile, TileContext ctx) {
        RunViews.RunView run = runOf(ctx);
        if (run == null) {
            return TileResult.noData(PAID_OWED, "No run yet");
        }
        Link link = Link.to("/operations/payroll/" + run.id(), "tab", "owed");
        BigDecimal paid = run.paidAmount() == null ? BigDecimal.ZERO : new BigDecimal(run.paidAmount());
        int owedPeople = owed.owed(ctx.month()).size();
        return TileResult.ok(PAID_OWED,
                Value.rupees("Paid so far", paid, link),
                Value.count("People still owed", owedPeople, link));
    }

    private TileResult locks(TileDefinition tile, TileContext ctx) {
        YearMonth month = ctx.month();
        List<UUID> teacherIds = teachers.activeTeachers().stream().map(TeacherDirectory.TeacherInfo::id).toList();
        long teachersLocked = teacherIds.stream().filter(id -> teacherAttendance.isLocked(id, month)).count();
        List<UUID> managerIds = managers.allManagers().stream()
                .filter(ManagerQueries.ManagerRef::active)
                .map(ManagerQueries.ManagerRef::id)
                .toList();
        Map<UUID, ManagerMonth> months = managerIds.isEmpty() ? Map.of() : managerAttendance.months(managerIds, month);
        long managersLocked = months.values().stream()
                .filter(m -> m instanceof ManagerMonth.Found f && f.locked())
                .count();
        Link link = Link.to("/operations/payroll");
        return TileResult.ok(LOCKS,
                Value.text("Teachers", teachersLocked + " of " + teacherIds.size() + " locked", link),
                Value.text("Managers", managersLocked + " of " + managerIds.size() + " locked", link));
    }
}
