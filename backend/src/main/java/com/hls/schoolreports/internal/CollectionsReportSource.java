package com.hls.schoolreports.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
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
import com.hls.schoolbilling.api.SchoolReceivables.SchoolAgeing;
import com.hls.schoolbilling.api.SchoolReceivables.SchoolMonth;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The Collections report (spec 014b US2): what each School was billed for the month, what was collected and what is
 * outstanding, then the position as of today by days past due. Every figure is the School Billing figure for the same School;
 * Zone and organization totals are sums of the School rows, so they cannot differ.
 */
@Component
class CollectionsReportSource implements ReportSource {

    static final String KEY = "collections";
    private static final String NOT_PLACED = "Not placed";

    private static final List<Column> MONTH_COLUMNS = List.of(
            new Column("zone", "Zone", Unit.TEXT),
            new Column("billed", "Billed", Unit.RUPEES),
            new Column("collected", "Collected", Unit.RUPEES),
            new Column("outstanding", "Outstanding", Unit.RUPEES),
            new Column("dueOn", "Due on", Unit.TEXT));

    private static final List<Column> AGEING_COLUMNS = List.of(
            new Column("zone", "Zone", Unit.TEXT),
            new Column("outstanding", "Outstanding", Unit.RUPEES),
            new Column("notYetDue", "Not yet due", Unit.RUPEES),
            new Column("d0to30", "0 to 30", Unit.RUPEES),
            new Column("d31to60", "31 to 60", Unit.RUPEES),
            new Column("d61to90", "61 to 90", Unit.RUPEES),
            new Column("over90", "Over 90", Unit.RUPEES));

    private final SchoolReceivables receivables;
    private final SchoolScope scope;
    private final BusinessDate businessDate;

    CollectionsReportSource(SchoolReceivables receivables, SchoolScope scope, BusinessDate businessDate) {
        this.businessDate = businessDate;
        this.receivables = receivables;
        this.scope = scope;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY, "Collections", PermissionModule.REPORT_COLLECTIONS, Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL), true, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new ReportPart("month", "Billed and collected", MONTH_COLUMNS, this::month),
                new ReportPart("ageing", "Ageing", AGEING_COLUMNS, this::ageing));
    }

    private Section month(ReportContext ctx) {
        List<SchoolInfo> schools = scope.inScope(ctx);
        Map<UUID, List<SchoolMonth>> months = schools.isEmpty()
                ? Map.of()
                : scope.isolated(() -> receivables.schoolMonths(ids(schools), ctx.month(), ctx.month()));
        Map<UUID, BigDecimal[]> figures = new LinkedHashMap<>();
        Map<UUID, String> dueOn = new LinkedHashMap<>();
        for (SchoolInfo s : schools) {
            List<SchoolMonth> list = months.get(s.id());
            if (list == null || list.isEmpty()) {
                continue; // never billed in this month: no row
            }
            SchoolMonth m = list.get(0);
            figures.put(s.id(), new BigDecimal[] {m.owed(), m.collected(), m.balance().max(BigDecimal.ZERO)});
            dueOn.put(s.id(), m.dueOn() == null ? "" : m.dueOn().toString());
        }
        return build("month", "Billed and collected", MONTH_COLUMNS, schools, figures, dueOn, true, ctx);
    }

    private Section ageing(ReportContext ctx) {
        List<SchoolInfo> schools = scope.inScope(ctx);
        LocalDate today = businessDate.today();
        Map<UUID, SchoolAgeing> ageing =
                schools.isEmpty() ? Map.of() : scope.isolated(() -> receivables.ageing(ids(schools), today));
        Map<UUID, BigDecimal[]> figures = new LinkedHashMap<>();
        for (SchoolInfo s : schools) {
            SchoolAgeing a = ageing.get(s.id());
            if (a == null) {
                continue;
            }
            BigDecimal parts = a.notYetDue().add(a.d0to30()).add(a.d31to60()).add(a.d61to90()).add(a.over90());
            if (parts.compareTo(a.outstanding()) != 0) {
                throw new IllegalStateException("Ageing buckets do not add up to outstanding for School " + s.id());
            }
            figures.put(s.id(), new BigDecimal[] {a.outstanding(), a.notYetDue(), a.d0to30(), a.d31to60(), a.d61to90(), a.over90()});
        }
        return build("ageing", "Ageing", AGEING_COLUMNS, schools, figures, Map.of(), false, ctx);
    }

    private static List<UUID> ids(List<SchoolInfo> schools) {
        return schools.stream().map(SchoolInfo::id).toList();
    }

    /** One row per School with figures, a total row per Zone, and the organization total as the totals row. */
    private Section build(
            String key,
            String title,
            List<Column> columns,
            List<SchoolInfo> schools,
            Map<UUID, BigDecimal[]> figures,
            Map<UUID, String> dueOn,
            boolean withDueOn,
            ReportContext ctx) {
        Map<UUID, String> zoneNames = scope.zoneNames(ctx);
        Map<UUID, List<SchoolInfo>> byZone = new LinkedHashMap<>();
        for (SchoolInfo s : schools) {
            if (figures.containsKey(s.id())) {
                byZone.computeIfAbsent(s.zoneId(), z -> new ArrayList<>()).add(s);
            }
        }
        int width = withDueOn ? 3 : 6;
        List<Row> rows = new ArrayList<>();
        BigDecimal[] all = zeros(width);
        byZone.forEach((zoneId, list) -> {
            String zone = zoneId == null ? NOT_PLACED : zoneNames.getOrDefault(zoneId, "-");
            BigDecimal[] sum = zeros(width);
            for (SchoolInfo s : list) {
                BigDecimal[] v = figures.get(s.id());
                rows.add(row(s.id().toString(), s.name(), zone, v, withDueOn, dueOn.get(s.id())));
                add(sum, v);
            }
            rows.add(row("zone:" + zoneId, "Zone total: " + zone, zone, sum, withDueOn, ""));
            add(all, sum);
        });
        Row total = row("total", "All", "", all, withDueOn, "");
        String note = rows.isEmpty() ? "No Schools with figures for this selection." : null;
        return new Section(key, title, columns, rows, total, false, note);
    }

    private static Row row(String id, String label, String zone, BigDecimal[] values, boolean withDueOn, String dueOn) {
        List<Cell> cells = new ArrayList<>();
        cells.add(Cell.of(zone));
        for (BigDecimal v : values) {
            cells.add(Cell.of(v));
        }
        if (withDueOn) {
            cells.add(dueOn == null || dueOn.isEmpty() ? Cell.noData() : Cell.of(dueOn));
        }
        return new Row(id, label, cells);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] z = new BigDecimal[n];
        Arrays.fill(z, BigDecimal.ZERO);
        return z;
    }

    private static void add(BigDecimal[] into, BigDecimal[] v) {
        for (int i = 0; i < into.length; i++) {
            into[i] = into[i].add(v[i]);
        }
    }

    @Override
    public MonthState monthState(ReportContext ctx) {
        return MonthState.NONE;
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return List.of("Ageing as of " + businessDate.today() + ".");
    }

    @Override
    public Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope scope) {
        return Optional.of(YearMonth.from(businessDate.today()));
    }
}
