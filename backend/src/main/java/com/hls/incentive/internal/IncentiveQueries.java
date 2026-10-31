package com.hls.incentive.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.incentive.internal.IncentiveAccess.Caller;
import com.hls.incentive.internal.IncentiveDtos.Detail;
import com.hls.incentive.internal.IncentiveDtos.HistoryEntry;
import com.hls.incentive.internal.IncentiveDtos.ManagerMonthTotal;
import com.hls.incentive.internal.IncentiveDtos.NoEarnerRow;
import com.hls.incentive.internal.IncentiveDtos.Row;
import com.hls.incentive.internal.IncentiveDtos.StatusTotal;
import com.hls.incentive.internal.IncentiveDtos.Summary;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists, detail, summary and the no-earner list (spec 030 US4). Every answer is filtered by the caller's scope. Names of
 * Schools, Zones, Managers and deciders are looked up in batches for the rows found, never per row; the payroll status text
 * (one read of the payroll intake) is added only to the rows that are returned. A few hundred incentives a year makes
 * filtering, sorting and paging after the batch lookups the simplest correct way.
 */
@Service
public class IncentiveQueries {

    static final int MAX_PAGE_SIZE = 100;

    /** What a list is narrowed to. All parts are optional. */
    public record Filter(
            IncentiveStatus status, UUID managerId, UUID zoneId, YearMonth month, String q, String sort, boolean descending) {

        public static Filter none() {
            return new Filter(null, null, null, null, null, null, true);
        }
    }

    private final IncentiveRepository incentives;
    private final NoEarnerRepository noEarners;
    private final IncentiveEventRepository events;
    private final IncentiveAccess access;
    private final PayrollLink payroll;
    private final PermissionGuard guard;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final AppUserRepository users;

    IncentiveQueries(
            IncentiveRepository incentives,
            NoEarnerRepository noEarners,
            IncentiveEventRepository events,
            IncentiveAccess access,
            PayrollLink payroll,
            PermissionGuard guard,
            SchoolDirectory schools,
            ManagerQueries managers,
            AppUserRepository users) {
        this.incentives = incentives;
        this.noEarners = noEarners;
        this.events = events;
        this.access = access;
        this.payroll = payroll;
        this.guard = guard;
        this.schools = schools;
        this.managers = managers;
        this.users = users;
    }

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public PageResponse<Row> list(Caller caller, Filter filter, int page, int size) {
        List<Row> all = rows(caller, filter, false);
        return pageOf(caller, all, page, size);
    }

    /** The caller's own incentives (any Manager), newest first by default. */
    @Transactional(readOnly = true)
    public PageResponse<Row> mine(Caller caller, Filter filter, int page, int size) {
        List<Row> all = rows(caller, filter, true);
        return pageOf(caller, all, page, size);
    }

    /** Every row matching the filter in the caller's scope, with no payroll text (the export and the summary use this). */
    @Transactional(readOnly = true)
    public List<Row> all(Caller caller, Filter filter) {
        return rows(caller, filter, false);
    }

    private PageResponse<Row> pageOf(Caller caller, List<Row> all, int page, int size) {
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(all.size(), safePage * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        Map<UUID, Incentive> byId = incentives.findAllById(all.subList(from, to).stream().map(Row::id).toList()).stream()
                .collect(Collectors.toMap(Incentive::getId, Function.identity()));
        List<Row> content = new ArrayList<>();
        for (Row row : all.subList(from, to)) {
            content.add(withPayroll(row, byId.get(row.id())));
        }
        return new PageResponse<>(content, safePage, safeSize, all.size());
    }

    private List<Row> rows(Caller caller, Filter filter, boolean onlyMine) {
        Specification<Incentive> spec = access.visibleTo(caller).and(narrowed(filter));
        if (onlyMine) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("earnerUserId"), caller.userId()));
        }
        List<Incentive> found = incentives.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Row> built = build(caller, found);
        String q = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            built = built.stream()
                    .filter(r -> contains(r.schoolName(), q) || contains(r.earnerName(), q) || contains(r.zoneName(), q))
                    .toList();
        }
        List<Row> sorted = new ArrayList<>(built);
        Comparator<Row> order = comparator(filter.sort());
        sorted.sort(filter.descending() ? order.reversed() : order);
        return sorted;
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static Comparator<Row> comparator(String sort) {
        Comparator<Row> byClosure = Comparator.comparing(Row::closureDate);
        if (sort == null) {
            return byClosure;
        }
        return switch (sort) {
            case "amount" -> Comparator.comparing(Row::amount).thenComparing(byClosure);
            case "school" -> Comparator.comparing((Row r) -> lower(r.schoolName())).thenComparing(byClosure);
            case "earner" -> Comparator.comparing((Row r) -> lower(r.earnerName())).thenComparing(byClosure);
            case "status" -> Comparator.comparing(Row::status).thenComparing(byClosure);
            default -> byClosure;
        };
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static Specification<Incentive> narrowed(Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (filter.status() != null) {
                all.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.managerId() != null) {
                all.add(cb.equal(root.get("earnerManagerId"), filter.managerId()));
            }
            if (filter.zoneId() != null) {
                all.add(cb.equal(root.get("zoneId"), filter.zoneId()));
            }
            if (filter.month() != null) {
                all.add(cb.or(
                        cb.between(root.get("closureDate"), filter.month().atDay(1), filter.month().atEndOfMonth()),
                        cb.equal(root.get("runMonth"), filter.month().atDay(1))));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public Detail detail(Caller caller, UUID id) {
        Incentive incentive = access.visible(caller, id);
        Row row = withPayroll(build(caller, List.of(incentive)).get(0), incentive);
        List<IncentiveEvent> history = events.findByIncentiveIdOrderBySeqAsc(id);
        Map<UUID, String> names = userNames(history.stream().map(IncentiveEvent::getActorId).collect(Collectors.toSet()));
        List<HistoryEntry> entries = history.stream()
                .map(e -> new HistoryEntry(
                        e.getKind(), e.getActorId() == null ? "System" : names.getOrDefault(e.getActorId(), "Unknown"), e.getDetail(), e.getCreatedAt()))
                .toList();
        return new Detail(row, entries);
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public Summary summary(Caller caller, YearMonth from, YearMonth to) {
        Specification<Incentive> range = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("closureDate"), from.atDay(1)));
            }
            if (to != null) {
                all.add(cb.lessThanOrEqualTo(root.get("closureDate"), to.atEndOfMonth()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        List<Incentive> found = incentives.findAll(access.visibleTo(caller).and(range));
        Map<IncentiveStatus, long[]> counts = new EnumMap<>(IncentiveStatus.class);
        Map<IncentiveStatus, BigDecimal> amounts = new EnumMap<>(IncentiveStatus.class);
        for (IncentiveStatus status : IncentiveStatus.values()) {
            counts.put(status, new long[1]);
            amounts.put(status, BigDecimal.ZERO);
        }
        Map<String, ManagerMonthTotal> perMonth = new TreeMap<>();
        Map<UUID, String> managerNames = managerNames();
        BigDecimal total = BigDecimal.ZERO;
        for (Incentive i : found) {
            counts.get(i.getStatus())[0]++;
            amounts.merge(i.getStatus(), i.getAmount(), BigDecimal::add);
            if (i.getStatus() == IncentiveStatus.REJECTED) {
                continue;
            }
            total = total.add(i.getAmount());
            String month = YearMonth.from(i.getClosureDate()).toString();
            String key = i.getEarnerManagerId() + "|" + month;
            perMonth.merge(
                    key,
                    new ManagerMonthTotal(i.getEarnerManagerId(), managerNames.getOrDefault(i.getEarnerManagerId(), "Unknown"), month, 1, i.getAmount()),
                    (a, b) -> new ManagerMonthTotal(a.managerId(), a.managerName(), a.month(), a.count() + 1, a.amount().add(b.amount())));
        }
        List<StatusTotal> byStatus = new ArrayList<>();
        for (IncentiveStatus status : IncentiveStatus.values()) {
            byStatus.add(new StatusTotal(status.name(), counts.get(status)[0], amounts.get(status)));
        }
        return new Summary(byStatus, new ArrayList<>(perMonth.values()), total);
    }

    // ------------------------------------------------------------------ no earner

    /** Schools with an approved MoU and no incentive, for Admin and Director. The controller checks the role. */
    @Transactional(readOnly = true)
    public List<NoEarnerRow> noEarner() {
        List<NoEarnerRecord> found = noEarners.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, SchoolInfo> schoolInfo = schoolsById(found.stream().map(NoEarnerRecord::getSchoolId).toList());
        Map<UUID, String> zoneNames = zoneNames(schoolInfo.values().stream().map(SchoolInfo::zoneId).collect(Collectors.toSet()));
        return found.stream()
                .map(n -> {
                    SchoolInfo s = schoolInfo.get(n.getSchoolId());
                    return new NoEarnerRow(
                            n.getId(),
                            n.getSchoolId(),
                            s == null ? "Unknown School" : s.name(),
                            s == null ? "" : zoneNames.getOrDefault(s.zoneId(), ""),
                            n.getContractId(),
                            n.getReason().name(),
                            reasonText(n.getReason()),
                            n.getDetail(),
                            n.getResolvedAt() != null,
                            n.getCreatedAt());
                })
                .toList();
    }

    static String reasonText(NoEarnerReason reason) {
        return switch (reason) {
            case NO_PROSPECT -> "No won prospect is linked to this School";
            case NO_OWNER -> "The prospect owner is no longer an active Manager";
            case OWNER_NOT_MANAGER -> "The prospect owner is not a payroll Manager";
            case APPROVED_BEFORE_APPROVAL_STEP -> "Excluded: the MoU was approved before the approval step";
            case BEFORE_GO_LIVE -> "Excluded: the MoU was approved before incentives began";
        };
    }

    // ------------------------------------------------------------------ building rows

    private List<Row> build(Caller caller, Collection<Incentive> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Set<UUID> schoolIds = found.stream().map(Incentive::getSchoolId).collect(Collectors.toSet());
        Map<UUID, SchoolInfo> schoolInfo = schoolsById(schoolIds);
        Map<UUID, String> zoneNames = zoneNames(schoolInfo.values().stream().map(SchoolInfo::zoneId).collect(Collectors.toSet()));
        Map<UUID, String> managerNames = managerNames();
        Map<UUID, String> deciders = userNames(found.stream().map(Incentive::getDecidedBy).collect(Collectors.toSet()));
        ScopeView scope = access.orgWide(caller) ? null : access.scope(caller);
        boolean approveGrant = guard.isGranted(caller.roles(), PermissionModule.MANAGER_INCENTIVES, PermissionAction.APPROVE);
        List<Row> rows = new ArrayList<>();
        for (Incentive i : found) {
            SchoolInfo school = schoolInfo.get(i.getSchoolId());
            UUID zoneId = school == null ? i.getZoneId() : school.zoneId();
            boolean decider = approveGrant
                    && (access.director(caller) || (scope != null && access.zoneManagerOf(caller, scope, i.getSchoolId(), zoneId)));
            rows.add(new Row(
                    i.getId(),
                    i.getSchoolId(),
                    school == null ? "Unknown School" : school.name(),
                    zoneNames.getOrDefault(zoneId, ""),
                    i.getEarnerManagerId(),
                    managerNames.getOrDefault(i.getEarnerManagerId(), "Unknown"),
                    i.getContractId(),
                    i.getClosureDate(),
                    i.getAmount(),
                    i.getStatus().name(),
                    i.isMouChanged(),
                    i.getHoldReason(),
                    i.getDecidedBy(),
                    i.getDecidedBy() == null ? null : deciders.getOrDefault(i.getDecidedBy(), "Unknown"),
                    i.getDecidedAt(),
                    i.isApprovedByEarner(),
                    i.getRejectReason(),
                    i.getRunMonth(),
                    null,
                    i.getVersion(),
                    actions(i.getStatus(), decider)));
        }
        return rows;
    }

    private static List<String> actions(IncentiveStatus status, boolean decider) {
        if (!decider) {
            return List.of();
        }
        return switch (status) {
            case PENDING_APPROVAL -> List.of("approve", "reject");
            case REJECTED -> List.of("reopen");
            default -> List.of();
        };
    }

    private Row withPayroll(Row row, Incentive incentive) {
        String text = incentive == null ? null : payroll.text(incentive);
        return new Row(
                row.id(), row.schoolId(), row.schoolName(), row.zoneName(), row.earnerManagerId(), row.earnerName(), row.contractId(),
                row.closureDate(), row.amount(), row.status(), row.mouChanged(), row.holdReason(), row.decidedById(), row.decidedBy(),
                row.decidedAt(), row.approvedByEarner(), row.rejectReason(), row.runMonth(), text, row.version(), row.actions());
    }

    private Map<UUID, SchoolInfo> schoolsById(Collection<UUID> ids) {
        Map<UUID, SchoolInfo> result = new HashMap<>();
        for (SchoolInfo s : schools.schools(ids)) {
            result.put(s.id(), s);
        }
        return result;
    }

    private Map<UUID, String> zoneNames(Collection<UUID> ids) {
        Map<UUID, String> result = new HashMap<>();
        for (ZoneInfo z : schools.zones(ids)) {
            result.put(z.id(), z.name());
        }
        return result;
    }

    private Map<UUID, String> managerNames() {
        Map<UUID, String> result = new HashMap<>();
        for (ManagerRef m : managers.allManagers()) {
            result.put(m.id(), m.displayName());
        }
        return result;
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (AppUser u : users.findAllById(wanted)) {
            result.put(u.getId(), u.getDisplayName());
        }
        return result;
    }
}
