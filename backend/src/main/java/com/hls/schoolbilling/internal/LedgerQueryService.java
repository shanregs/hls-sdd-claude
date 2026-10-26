package com.hls.schoolbilling.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import com.hls.schoolbilling.internal.ReceivablesDtos.*;
import com.hls.schoolbilling.internal.ReceivablesStore.DueRecord;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads of the ledger: dues, one School's ledger, the overview, ageing and the not-billed list (spec 022 US1, US2, US5). */
@Service
public class LedgerQueryService {

    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ReceivablesStore store;
    private final BillingScope scope;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;
    private final DueCalculator calculator;
    private final BusinessDate businessDate;

    LedgerQueryService(
            ReceivablesStore store,
            BillingScope scope,
            SchoolDirectory schools,
            ManagerQueries managers,
            TeacherDirectory teachers,
            DueCalculator calculator,
            BusinessDate businessDate) {
        this.store = store;
        this.scope = scope;
        this.schools = schools;
        this.managers = managers;
        this.teachers = teachers;
        this.calculator = calculator;
        this.businessDate = businessDate;
    }

    // ---- derived status

    public static String statusOf(DueRecord d) {
        if (d.cancelled()) {
            return "CANCELLED";
        }
        if (d.balance().signum() <= 0) {
            return "PAID";
        }
        if (d.adjustments().signum() > 0 || d.collected().signum() > 0) {
            return "PART_PAID";
        }
        return "OPEN";
    }

    public static boolean overdue(DueRecord d, LocalDate today) {
        return !d.cancelled() && d.balance().signum() > 0 && d.dueOn().isBefore(today);
    }

    DueRow row(DueRecord d, Map<UUID, String> schoolNames, LocalDate today) {
        boolean overdue = overdue(d, today);
        return new DueRow(
                d.id(),
                d.reference(),
                d.schoolId(),
                schoolNames.getOrDefault(d.schoolId(), ""),
                d.month().toString(),
                d.raisedOn(),
                d.dueOn(),
                d.total(),
                d.adjustments(),
                d.collected(),
                d.balance(),
                statusOf(d),
                overdue,
                overdue ? ChronoUnit.DAYS.between(d.dueOn(), today) : 0,
                d.cancelReason(),
                d.cancelledAt());
    }

    DueRow row(DueRecord d) {
        Map<UUID, String> names = schools.schools(List.of(d.schoolId())).stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
        return row(d, names, businessDate.today());
    }

    private Map<UUID, String> namesOf(Set<UUID> ids) {
        return schools.schools(ids).stream().collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
    }

    // ---- dues

    @Transactional(readOnly = true)
    public PageResponse<DueRow> dues(BillingCaller caller, UUID schoolId, YearMonth month, String status, int page, int size) {
        LocalDate today = businessDate.today();
        List<DueRecord> records;
        if (schoolId != null) {
            scope.requireSchool(caller, schoolId);
            records = store.duesOfSchool(schoolId);
        } else {
            records = store.duesOfSchools(scope.schoolIdsInScope(caller));
        }
        Map<UUID, String> names = namesOf(records.stream().map(DueRecord::schoolId).collect(Collectors.toSet()));
        List<DueRow> rows = records.stream()
                .filter(d -> month == null || d.month().equals(month))
                .map(d -> row(d, names, today))
                .filter(r -> status == null || status.isBlank() || r.status().equalsIgnoreCase(status))
                .sorted(Comparator.comparing(DueRow::month).reversed().thenComparing(DueRow::schoolName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new PageResponse<>(rows.subList(from, to), page, size, rows.size());
    }

    @Transactional(readOnly = true)
    public DueDetail detail(BillingCaller caller, UUID dueId) {
        DueRecord d = store.due(dueId).orElseThrow(() -> new NotFoundException("Monthly due not found."));
        scope.requireSchool(caller, d.schoolId());
        List<ReceivablesStore.LineRow> lines = store.lines(dueId);
        Map<UUID, TeacherInfo> names = teachers.teacherInfo(lines.stream().map(ReceivablesStore.LineRow::teacherId).collect(Collectors.toSet()));
        List<DueLineView> views = lines.stream()
                .map(l -> new DueLineView(
                        l.id(),
                        l.teacherId(),
                        names.containsKey(l.teacherId()) ? names.get(l.teacherId()).name() : "",
                        l.positionNumber(),
                        l.salary(),
                        l.from(),
                        l.to(),
                        l.worked(),
                        l.monthDays(),
                        l.full(),
                        l.amount(),
                        l.full()
                                ? "Whole month at " + l.salary().toPlainString()
                                : l.salary().toPlainString() + " x " + l.worked().toPlainString() + " working days at the School / "
                                        + l.monthDays().toPlainString() + " working days in the month"))
                .toList();
        return new DueDetail(
                row(d), views, store.adjustmentsOfDue(dueId), store.allocationsOfDue(dueId), store.followUpTask(dueId).orElse(null));
    }

    // ---- one School

    @Transactional(readOnly = true)
    public SchoolLedger schoolLedger(BillingCaller caller, UUID schoolId) {
        SchoolInfo school = scope.requireSchool(caller, schoolId);
        LocalDate today = businessDate.today();
        List<DueRecord> dues = store.duesOfSchool(schoolId);
        Map<UUID, String> names = Map.of(schoolId, school.name());
        BigDecimal owed = ZERO;
        BigDecimal collected = ZERO;
        BigDecimal outstanding = ZERO;
        BigDecimal overdue = ZERO;
        for (DueRecord d : dues) {
            if (d.cancelled()) {
                continue;
            }
            owed = owed.add(d.total().subtract(d.adjustments()));
            collected = collected.add(d.collected());
            outstanding = outstanding.add(d.balance());
            if (overdue(d, today)) {
                overdue = overdue.add(d.balance());
            }
        }
        return new SchoolLedger(
                schoolId,
                school.name(),
                owed,
                collected,
                outstanding,
                overdue,
                store.advanceCredit(schoolId),
                dues.stream().map(d -> row(d, names, today)).toList(),
                store.collectionsOfSchool(schoolId),
                store.adjustmentsOfSchool(schoolId));
    }

    // ---- overview

    private Map<UUID, BigDecimal> advanceCredits(Set<UUID> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> out = new HashMap<>();
        store.named()
                .query(
                        """
                        select c.school_id,
                               sum(c.amount - coalesce((select sum(a.amount) from collection_allocation a where a.collection_id = c.id), 0)) as credit
                        from school_collection c
                        where c.school_id in (:ids) and c.reverses_id is null
                          and not exists (select 1 from school_collection r where r.reverses_id = c.id)
                        group by c.school_id
                        """,
                        new MapSqlParameterSource("ids", schoolIds),
                        rs -> {
                            out.put(rs.getObject("school_id", UUID.class), rs.getBigDecimal("credit"));
                        });
        return out;
    }

    @Transactional(readOnly = true)
    public Overview overview(BillingCaller caller, YearMonth month, UUID zoneId, UUID managerId, UUID schoolId) {
        LocalDate today = businessDate.today();
        List<SchoolInfo> inScope = filteredSchools(caller, zoneId, managerId, schoolId);
        Set<UUID> ids = inScope.stream().map(SchoolInfo::id).collect(Collectors.toSet());
        Map<UUID, List<DueRecord>> bySchool = store.duesOfSchools(ids).stream()
                .filter(d -> !d.cancelled() && d.month().equals(month))
                .collect(Collectors.groupingBy(DueRecord::schoolId));
        Map<UUID, BigDecimal> credits = advanceCredits(ids);
        Map<UUID, ManagerRef> managerOf = managers.managersOfSchools(ids);
        Map<UUID, String> zoneNames = zoneNames(inScope);
        BigDecimal owed = ZERO;
        BigDecimal collected = ZERO;
        BigDecimal outstanding = ZERO;
        BigDecimal overdue = ZERO;
        BigDecimal credit = ZERO;
        List<OverviewRow> rows = new ArrayList<>();
        for (SchoolInfo s : inScope) {
            List<DueRecord> dues = bySchool.getOrDefault(s.id(), List.of());
            BigDecimal o = ZERO;
            BigDecimal c = ZERO;
            BigDecimal out = ZERO;
            BigDecimal od = ZERO;
            for (DueRecord d : dues) {
                o = o.add(d.total().subtract(d.adjustments()));
                c = c.add(d.collected());
                out = out.add(d.balance());
                if (overdue(d, today)) {
                    od = od.add(d.balance());
                }
            }
            BigDecimal adv = credits.getOrDefault(s.id(), ZERO);
            if (dues.isEmpty() && adv.signum() == 0) {
                continue;
            }
            ManagerRef m = managerOf.get(s.id());
            rows.add(new OverviewRow(
                    s.id(), s.name(), s.zoneId(), zoneNames.getOrDefault(s.zoneId(), ""), m == null ? null : m.id(),
                    m == null ? null : m.displayName(), o, c, out, od, adv));
            owed = owed.add(o);
            collected = collected.add(c);
            outstanding = outstanding.add(out);
            overdue = overdue.add(od);
            credit = credit.add(adv);
        }
        rows.sort(Comparator.comparing(OverviewRow::schoolName, String.CASE_INSENSITIVE_ORDER));
        return new Overview(month.toString(), owed, collected, outstanding, overdue, credit, rows);
    }

    private List<SchoolInfo> filteredSchools(BillingCaller caller, UUID zoneId, UUID managerId, UUID schoolId) {
        List<SchoolInfo> inScope = scope.schoolsInScope(caller);
        Set<UUID> ofManager = null;
        if (managerId != null) {
            ofManager = managers.currentSchoolIds(List.of(managerId)).getOrDefault(managerId, Set.of());
        }
        final Set<UUID> managerSchools = ofManager;
        return inScope.stream()
                .filter(s -> zoneId == null || zoneId.equals(s.zoneId()))
                .filter(s -> schoolId == null || schoolId.equals(s.id()))
                .filter(s -> managerSchools == null || managerSchools.contains(s.id()))
                .toList();
    }

    private Map<UUID, String> zoneNames(List<SchoolInfo> list) {
        Set<UUID> zoneIds = list.stream().map(SchoolInfo::zoneId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return schools.zones(zoneIds).stream().collect(Collectors.toMap(ZoneInfo::id, ZoneInfo::name));
    }

    // ---- ageing

    private static BigDecimal[] bucketsOf(long days, BigDecimal amount) {
        BigDecimal[] b = {ZERO, ZERO, ZERO, ZERO, ZERO};
        int i = days <= 0 ? 0 : days <= 30 ? 1 : days <= 60 ? 2 : days <= 90 ? 3 : 4;
        b[i] = amount;
        return b;
    }

    private static AgeingRow toRow(UUID schoolId, String name, UUID zoneId, String zone, UUID managerId, String manager, BigDecimal[] s) {
        BigDecimal total = s[0].add(s[1]).add(s[2]).add(s[3]).add(s[4]);
        return new AgeingRow(schoolId, name, zoneId, zone, managerId, manager, s[0], s[1], s[2], s[3], s[4], total);
    }

    private static void add(BigDecimal[] into, BigDecimal[] other) {
        for (int i = 0; i < into.length; i++) {
            into[i] = into[i].add(other[i]);
        }
    }

    @Transactional(readOnly = true)
    public Ageing ageing(BillingCaller caller, LocalDate asOf, UUID zoneId, UUID managerId) {
        LocalDate date = asOf == null ? businessDate.today() : asOf;
        List<SchoolInfo> inScope = filteredSchools(caller, zoneId, managerId, null);
        Set<UUID> ids = inScope.stream().map(SchoolInfo::id).collect(Collectors.toSet());
        Map<UUID, ManagerRef> managerOf = managers.managersOfSchools(ids);
        Map<UUID, String> zoneNames = zoneNames(inScope);
        Map<UUID, BigDecimal[]> perSchool = new HashMap<>();
        for (DueRecord d : store.duesOfSchools(ids)) {
            if (d.cancelled() || d.balance().signum() <= 0) {
                continue;
            }
            add(perSchool.computeIfAbsent(d.schoolId(), k -> new BigDecimal[] {ZERO, ZERO, ZERO, ZERO, ZERO}),
                    bucketsOf(ChronoUnit.DAYS.between(d.dueOn(), date), d.balance()));
        }
        BigDecimal[] all = {ZERO, ZERO, ZERO, ZERO, ZERO};
        Map<UUID, BigDecimal[]> perZone = new LinkedHashMap<>();
        Map<UUID, BigDecimal[]> perManager = new LinkedHashMap<>();
        Map<UUID, String> managerNames = new HashMap<>();
        List<AgeingRow> schoolRows = new ArrayList<>();
        for (SchoolInfo s : inScope) {
            BigDecimal[] b = perSchool.get(s.id());
            if (b == null) {
                continue;
            }
            ManagerRef m = managerOf.get(s.id());
            schoolRows.add(toRow(s.id(), s.name(), s.zoneId(), zoneNames.getOrDefault(s.zoneId(), ""),
                    m == null ? null : m.id(), m == null ? null : m.displayName(), b));
            add(all, b);
            add(perZone.computeIfAbsent(s.zoneId(), k -> new BigDecimal[] {ZERO, ZERO, ZERO, ZERO, ZERO}), b);
            UUID key = m == null ? null : m.id();
            add(perManager.computeIfAbsent(key, k -> new BigDecimal[] {ZERO, ZERO, ZERO, ZERO, ZERO}), b);
            managerNames.put(key, m == null ? "No Zone Manager" : m.displayName());
        }
        schoolRows.sort(Comparator.comparing(AgeingRow::schoolName, String.CASE_INSENSITIVE_ORDER));
        List<AgeingRow> zoneRows = perZone.entrySet().stream()
                .map(e -> toRow(null, null, e.getKey(), zoneNames.getOrDefault(e.getKey(), ""), null, null, e.getValue()))
                .sorted(Comparator.comparing(AgeingRow::zoneName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<AgeingRow> managerRows = perManager.entrySet().stream()
                .map(e -> toRow(null, null, null, null, e.getKey(), managerNames.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(AgeingRow::managerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Ageing(date, toRow(null, null, null, null, null, null, all), schoolRows, zoneRows, managerRows);
    }

    // ---- not billed

    @Transactional(readOnly = true)
    public List<NotBilledRow> notBilled(BillingCaller caller, YearMonth month) {
        List<NotBilledRow> out = new ArrayList<>();
        for (SchoolInfo s : scope.schoolsInScope(caller)) {
            DueCalculator.Result r = calculator.compute(s.id(), month);
            if (r.notBilled().isEmpty()) {
                continue;
            }
            Map<UUID, TeacherInfo> names = teachers.teacherInfo(
                    r.notBilled().stream().map(DueCalculator.Unbilled::teacherId).collect(Collectors.toSet()));
            for (DueCalculator.Unbilled u : r.notBilled()) {
                out.add(new NotBilledRow(s.id(), s.name(), u.teacherId(),
                        names.containsKey(u.teacherId()) ? names.get(u.teacherId()).name() : "", u.reason()));
            }
        }
        return out;
    }

    // ---- CSV

    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    @Transactional(readOnly = true)
    public String exportOverview(BillingCaller caller, YearMonth month, UUID zoneId, UUID managerId, UUID schoolId) {
        Overview o = overview(caller, month, zoneId, managerId, schoolId);
        StringBuilder sb = new StringBuilder("School,Zone,Zone Manager,Owed,Collected,Outstanding,Overdue,Advance credit\r\n");
        for (OverviewRow r : o.schools()) {
            sb.append(String.join(",", csv(r.schoolName()), csv(r.zoneName()), csv(r.managerName()), r.owed().toPlainString(),
                    r.collected().toPlainString(), r.outstanding().toPlainString(), r.overdue().toPlainString(),
                    r.advanceCredit().toPlainString())).append("\r\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportAgeing(BillingCaller caller, LocalDate asOf, UUID zoneId, UUID managerId) {
        Ageing a = ageing(caller, asOf, zoneId, managerId);
        StringBuilder sb = new StringBuilder("School,Zone,Zone Manager,Not yet due,0 to 30,31 to 60,61 to 90,Over 90,Total\r\n");
        for (AgeingRow r : a.schools()) {
            sb.append(String.join(",", csv(r.schoolName()), csv(r.zoneName()), csv(r.managerName()), r.notYetDue().toPlainString(),
                    r.b0to30().toPlainString(), r.b31to60().toPlainString(), r.b61to90().toPlainString(), r.over90().toPlainString(),
                    r.total().toPlainString())).append("\r\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportLedger(BillingCaller caller, UUID schoolId) {
        SchoolLedger l = schoolLedger(caller, schoolId);
        StringBuilder sb = new StringBuilder("Reference,Month,Raised,Due,Total,Adjustments,Collected,Balance,Status\r\n");
        for (DueRow r : l.dues()) {
            sb.append(String.join(",", csv(r.reference()), r.month(), r.raisedOn().toString(), r.dueOn().toString(), r.total().toPlainString(),
                    r.adjustments().toPlainString(), r.collected().toPlainString(), r.balance().toPlainString(), r.status()))
                    .append("\r\n");
        }
        return sb.toString();
    }
}
