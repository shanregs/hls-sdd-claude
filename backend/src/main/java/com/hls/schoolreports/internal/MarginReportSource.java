package com.hls.schoolreports.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayrollFigures;
import com.hls.payroll.api.PayrollFigures.PersonFigures;
import com.hls.payroll.api.PayrollFigures.RunSummary;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.MonthState;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportScope;
import com.hls.reporting.api.ReportSource;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.schoolbilling.api.SchoolReceivables.TeacherSchoolBilled;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Margin report (spec 014b US1): the School fee (the 022 due line) less the Teacher pay (the 013b payslip net), per Teacher
 * and School, with totals per School and Zone. A Teacher billed at two Schools has their net split by the working days each due
 * line billed ({@link PaySplit}), so fee and pay agree by construction. A fee with no payable payslip, or the reverse, is "not
 * matched" with the reason and is left out of every total, never shown as zero.
 */
@Component
class MarginReportSource implements ReportSource {

    static final String KEY = "margin";
    static final String DRAFT_NOTE = "Payroll not approved, figures may change.";
    private static final Logger LOG = LoggerFactory.getLogger(MarginReportSource.class);
    private static final String NOT_PLACED = "Not placed";

    private static final List<Column> TOTAL_COLUMNS = List.of(
            new Column("zone", "Zone", Unit.TEXT),
            new Column("matched", "Teachers matched", Unit.COUNT),
            new Column("fee", "Fee", Unit.RUPEES),
            new Column("pay", "Pay", Unit.RUPEES),
            new Column("margin", "Margin", Unit.RUPEES));

    private static final List<Column> TEACHER_COLUMNS = List.of(
            new Column("school", "School", Unit.TEXT),
            new Column("zone", "Zone", Unit.TEXT),
            new Column("days", "Billed days", Unit.DAYS),
            new Column("fee", "Fee", Unit.RUPEES),
            new Column("pay", "Pay", Unit.RUPEES),
            new Column("margin", "Margin", Unit.RUPEES),
            new Column("status", "Status", Unit.TEXT));

    /** One Teacher at one School (or, for a payslip with no fee, at the School the payslip says). */
    record Line(
            String id,
            UUID teacherId,
            String teacher,
            SchoolInfo school,
            BigDecimal days,
            BigDecimal fee,
            BigDecimal pay,
            boolean payKnown,
            boolean matched,
            String status) {}

    /** The lines of one request and whether the pay side could be read. */
    record Model(List<Line> lines, boolean runExists, boolean payrollDown, Map<UUID, String> zoneNames) {}

    private final SchoolReceivables receivables;
    private final PayrollFigures payroll;
    private final TeacherDirectory teachers;
    private final SchoolScope scope;
    private final BusinessDate businessDate;

    MarginReportSource(
            SchoolReceivables receivables,
            PayrollFigures payroll,
            TeacherDirectory teachers,
            SchoolScope scope,
            BusinessDate businessDate) {
        this.businessDate = businessDate;
        this.receivables = receivables;
        this.payroll = payroll;
        this.teachers = teachers;
        this.scope = scope;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY, "Margin", PermissionModule.REPORT_MARGIN, Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL), true, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("schools", "Schools", TOTAL_COLUMNS, ctx -> totals(ctx, "schools", "Schools", false)),
                new Part("zones", "Zones", TOTAL_COLUMNS, ctx -> totals(ctx, "zones", "Zones", true)),
                new Part("teachers", "Teachers", TEACHER_COLUMNS, this::teacherSection));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    // ---- Sections -------------------------------------------------------------------------------------------------

    private Section teacherSection(ReportContext ctx) {
        Model model = model(ctx);
        List<Row> rows = new ArrayList<>();
        for (Line l : model.lines()) {
            rows.add(new Row(
                    l.id(),
                    l.teacher(),
                    List.of(
                            Cell.of(schoolName(l.school())),
                            Cell.of(zoneName(l.school(), model)),
                            l.days() == null ? Cell.noData() : Cell.of(l.days()),
                            feeCell(l),
                            payCell(l, model),
                            marginCell(l, model),
                            Cell.of(l.status()))));
        }
        return Section.of("teachers", "Teachers", TEACHER_COLUMNS, rows, null);
    }

    private Section totals(ReportContext ctx, String key, String title, boolean byZone) {
        Model model = model(ctx);
        // grouped by id, not name: two Schools or Zones with the same name stay two rows
        Map<String, List<Line>> groups = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, String> zoneOf = new HashMap<>();
        for (Line l : model.lines()) {
            if (l.school() == null) {
                continue;
            }
            String zone = zoneName(l.school(), model);
            String id = byZone ? String.valueOf(l.school().zoneId()) : l.school().id().toString();
            groups.computeIfAbsent(id, n -> new ArrayList<>()).add(l);
            names.put(id, byZone ? zone : l.school().name());
            zoneOf.put(id, zone);
        }
        List<String> order = new ArrayList<>(groups.keySet());
        order.sort(Comparator.comparing((String id) -> names.get(id), String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder()));
        List<Row> rows = new ArrayList<>();
        for (String id : order) {
            rows.add(totalRow(id, names.get(id), zoneOf.get(id), groups.get(id), model));
        }
        Row all = totalRow("total", "All", "", model.lines().stream().filter(l -> l.school() != null).toList(), model);
        return Section.of(key, title, TOTAL_COLUMNS, rows, all);
    }

    private Row totalRow(String id, String label, String zone, List<Line> lines, Model model) {
        long matched = lines.stream().filter(Line::matched).map(Line::teacherId).distinct().count();
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal pay = BigDecimal.ZERO;
        for (Line l : lines) {
            if (model.runExists() && !l.matched()) {
                continue;
            }
            if (l.fee() != null) {
                fee = fee.add(l.fee());
            }
            if (l.matched() && l.pay() != null) {
                pay = pay.add(l.pay());
            }
        }
        Cell payCell = model.payrollDown() ? Cell.notAvailable() : model.runExists() ? Cell.of(pay) : Cell.noData();
        Cell marginCell = model.payrollDown() ? Cell.notAvailable() : model.runExists() ? Cell.of(fee.subtract(pay)) : Cell.noData();
        return new Row(
                id, label, List.of(Cell.of(zone), model.runExists() && !model.payrollDown() ? Cell.of(matched) : Cell.noData(), Cell.of(fee), payCell, marginCell));
    }

    private static Cell feeCell(Line l) {
        return l.fee() == null ? Cell.notMatched() : Cell.of(l.fee());
    }

    private static Cell payCell(Line l, Model model) {
        if (model.payrollDown()) {
            return Cell.notAvailable();
        }
        if (!model.runExists()) {
            return Cell.noData();
        }
        return l.payKnown() ? Cell.of(l.pay()) : Cell.notMatched();
    }

    private static Cell marginCell(Line l, Model model) {
        if (model.payrollDown()) {
            return Cell.notAvailable();
        }
        if (!model.runExists()) {
            return Cell.noData();
        }
        return l.matched() ? Cell.of(l.fee().subtract(l.pay())) : Cell.notMatched();
    }

    private static String schoolName(SchoolInfo school) {
        return school == null ? NOT_PLACED : school.name();
    }

    private static String zoneName(SchoolInfo school, Model model) {
        return school == null || school.zoneId() == null ? NOT_PLACED : model.zoneNames().getOrDefault(school.zoneId(), "-");
    }

    // ---- The lines ------------------------------------------------------------------------------------------------

    private Model model(ReportContext ctx) {
        return ctx.memo("margin.model", () -> load(ctx));
    }

    private Model load(ReportContext ctx) {
        YearMonth month = ctx.month();
        ReportScope reportScope = ctx.scope();
        List<SchoolInfo> everySchool = scope.all();
        Map<UUID, SchoolInfo> schoolById = scope.byId(everySchool);
        Set<UUID> visibleSchools = new HashSet<>();
        scope.inScope(ctx).forEach(s -> visibleSchools.add(s.id()));
        Map<UUID, String> zoneNames = scope.zoneNames(ctx);

        // fee: every School's lines, so a Teacher's pay can be split over all the Schools that billed them
        List<TeacherSchoolBilled> billed = everySchool.isEmpty()
                ? List.of()
                : receivables.billedPerTeacherAndSchool(null, everySchool.stream().map(SchoolInfo::id).toList(), month, month);
        Map<UUID, Map<UUID, Fee>> feeBy = new LinkedHashMap<>();
        for (TeacherSchoolBilled b : billed) {
            feeBy.computeIfAbsent(b.teacherId(), t -> new LinkedHashMap<>())
                    .merge(b.schoolId(), new Fee(b.amount(), b.workedDays()), Fee::plus);
        }

        // pay: optional, in its own read-only transaction
        boolean payrollDown = false;
        Optional<RunSummary> run = Optional.empty();
        Map<UUID, PersonFigures> slips = new HashMap<>();
        try {
            run = scope.isolated(() -> payroll.runOf(month));
            if (run.isPresent()) {
                List<PersonFigures> people = scope.isolated(() -> payroll.peopleOf(month));
                people.stream().filter(p -> p.kind() == Kind.TEACHER).forEach(p -> slips.put(p.personId(), p));
            }
        } catch (RuntimeException e) {
            LOG.warn("Margin report: payroll could not be read", e);
            payrollDown = true;
        }
        // a failed payslip read leaves the run known but unusable; fee must still show (FR-013)
        boolean runExists = run.isPresent() && !payrollDown;

        Set<UUID> teacherIds = new HashSet<>(feeBy.keySet());
        teacherIds.addAll(slips.keySet());
        Map<UUID, TeacherInfo> info = teacherIds.isEmpty() ? Map.of() : teachers.teacherInfo(teacherIds);
        List<Line> lines = new ArrayList<>();
        for (UUID teacherId : teacherIds) {
            Map<UUID, Fee> fees = feeBy.get(teacherId);
            PersonFigures slip = slips.get(teacherId);
            String name = info.containsKey(teacherId) ? info.get(teacherId).name() : slip != null ? slip.name() : "-";
            if (fees == null) {
                lineForPayslipOnly(ctx, reportScope, schoolById, name, slip, lines);
                continue;
            }
            addFeeLines(teacherId, name, fees, slip, runExists, payrollDown, visibleSchools, schoolById, lines);
        }
        lines.sort(Comparator.comparing(Line::teacher, String.CASE_INSENSITIVE_ORDER).thenComparing(l -> schoolName(l.school())).thenComparing(Line::id));
        return new Model(List.copyOf(lines), runExists, payrollDown, zoneNames);
    }

    private void addFeeLines(
            UUID teacherId,
            String name,
            Map<UUID, Fee> fees,
            PersonFigures slip,
            boolean runExists,
            boolean payrollDown,
            Set<UUID> visibleSchools,
            Map<UUID, SchoolInfo> schoolById,
            List<Line> lines) {
        boolean payable = slip != null && slip.payable() && slip.net() != null;
        Map<UUID, BigDecimal> split = null;
        String problem = null;
        if (runExists && !payrollDown) {
            if (slip == null) {
                problem = "not matched: no payslip for this month";
            } else if (!payable) {
                problem = "not matched: payslip cannot be paid";
            } else {
                Map<UUID, BigDecimal> days = new LinkedHashMap<>();
                fees.forEach((school, f) -> days.put(school, f.days()));
                if (days.values().stream().allMatch(d -> d.signum() <= 0)) {
                    problem = "not matched: no billed days to split the pay";
                } else {
                    split = PaySplit.split(slip.net(), days);
                }
            }
        }
        for (Map.Entry<UUID, Fee> e : fees.entrySet()) {
            if (!visibleSchools.contains(e.getKey())) {
                continue;
            }
            SchoolInfo school = schoolById.get(e.getKey());
            boolean matched = split != null;
            String status = payrollDown ? "pay not available" : !runExists ? "no pay run yet" : matched ? "matched" : problem;
            lines.add(new Line(
                    teacherId + ":" + e.getKey(),
                    teacherId,
                    name,
                    school,
                    e.getValue().days(),
                    e.getValue().amount(),
                    matched ? split.get(e.getKey()) : null,
                    matched,
                    matched,
                    status));
        }
    }

    /** A payable payslip with no fee line anywhere: shown only to a caller who may see the place the payslip says. */
    private void lineForPayslipOnly(
            ReportContext ctx, ReportScope reportScope, Map<UUID, SchoolInfo> schoolById, String name, PersonFigures slip, List<Line> lines) {
        if (slip == null || !slip.payable() || slip.net() == null) {
            return;
        }
        SchoolInfo school = slip.schoolId() == null ? null : schoolById.get(slip.schoolId());
        UUID zoneId = school != null ? school.zoneId() : slip.zoneId();
        boolean allowed = reportScope.orgWide() || (school != null && reportScope.allowsSchool(school.id(), school.zoneId()));
        UUID zoneFilter = ctx.request().zoneId();
        UUID schoolFilter = ctx.request().schoolId();
        if (!allowed
                || (zoneFilter != null && !zoneFilter.equals(zoneId))
                || (schoolFilter != null && (school == null || !schoolFilter.equals(school.id())))) {
            return;
        }
        lines.add(new Line(
                slip.personId() + ":none",
                slip.personId(),
                name,
                school,
                null,
                null,
                slip.net(),
                true,
                false,
                "not matched: no School fee for this month"));
    }

    private record Fee(BigDecimal amount, BigDecimal days) {

        Fee plus(Fee other) {
            return new Fee(amount.add(other.amount), days.add(other.days));
        }
    }

    // ---- Month state and default month ------------------------------------------------------------------------------

    @Override
    public MonthState monthState(ReportContext ctx) {
        Model model = model(ctx);
        if (model.payrollDown() || !model.runExists()) {
            return MonthState.NONE;
        }
        return switch (runState(ctx)) {
            case DRAFT, AWAITING_APPROVAL -> MonthState.DRAFT;
            case APPROVED -> MonthState.APPROVED;
            case PAID -> MonthState.PAID;
        };
    }

    private PayrollFigures.RunState runState(ReportContext ctx) {
        return ctx.memo("margin.runState", () -> payroll.runOf(ctx.month()).map(RunSummary::state).orElse(PayrollFigures.RunState.DRAFT));
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return state == MonthState.DRAFT ? List.of(DRAFT_NOTE) : List.of();
    }

    @Override
    public Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope reportScope) {
        YearMonth current = YearMonth.from(businessDate.today());
        try {
            return Optional.of(scope.isolated(() -> payroll.lastApprovedMonth()).orElse(current));
        } catch (RuntimeException e) {
            LOG.warn("Margin report: last approved month could not be read", e);
            return Optional.of(current);
        }
    }
}
