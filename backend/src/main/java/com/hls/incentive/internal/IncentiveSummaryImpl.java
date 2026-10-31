package com.hls.incentive.internal;

import com.hls.identity.user.Role;
import com.hls.incentive.api.IncentiveSummary;
import com.hls.incentive.internal.IncentiveAccess.Caller;
import com.hls.incentive.internal.IncentiveDtos.Row;
import com.hls.incentive.internal.IncentiveQueries.Filter;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The incentive counts and the pending list for the dashboard and reports (spec 030 FR-014, D-12). */
@Service
class IncentiveSummaryImpl implements IncentiveSummary {

    private final IncentiveRepository incentives;
    private final NoEarnerRepository noEarners;
    private final IncentiveQueries queries;
    private final IncentiveAccess access;
    private final SchoolDirectory schools;
    private final com.hls.organization.api.ManagerQueries managers;

    IncentiveSummaryImpl(
            IncentiveRepository incentives,
            NoEarnerRepository noEarners,
            IncentiveQueries queries,
            IncentiveAccess access,
            SchoolDirectory schools,
            com.hls.organization.api.ManagerQueries managers) {
        this.incentives = incentives;
        this.noEarners = noEarners;
        this.queries = queries;
        this.access = access;
        this.schools = schools;
        this.managers = managers;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts counts(UUID userId, Set<Role> roles, YearMonth month) {
        Caller caller = new Caller(userId, roles);
        List<Row> rows = queries.all(caller, Filter.none());
        long pending = rows.stream().filter(r -> r.status().equals("PENDING_APPROVAL")).count();
        long awaiting = rows.stream().filter(r -> r.status().equals("APPROVED") || r.status().equals("HELD")).count();
        BigDecimal paid = rows.stream()
                .filter(r -> r.status().equals("PAID_IN_RUN") && month.atDay(1).equals(r.runMonth()))
                .map(Row::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long noEarner = access.orgWide(caller) ? noEarners.countByResolvedAtIsNull() : 0;
        return new Counts(pending, awaiting, paid, noEarner);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingItem> pendingFor(UUID userId, Set<Role> roles) {
        Caller caller = new Caller(userId, roles);
        if (!access.director(caller) && !roles.contains(Role.MANAGER)) {
            return List.of();
        }
        List<Incentive> found = incentives.findAll(
                access.visibleTo(caller).and((root, query, cb) -> cb.equal(root.get("status"), IncentiveStatus.PENDING_APPROVAL)),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        if (found.isEmpty()) {
            return List.of();
        }
        ScopeView scope = access.orgWide(caller) ? null : access.scope(caller);
        Map<UUID, SchoolInfo> info = schools.schools(found.stream().map(Incentive::getSchoolId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolInfo::id, Function.identity()));
        Map<UUID, String> names = managers.allManagers().stream()
                .collect(Collectors.toMap(m -> m.id(), m -> m.displayName(), (a, b) -> a));
        List<PendingItem> items = new ArrayList<>();
        for (Incentive i : found) {
            SchoolInfo school = info.get(i.getSchoolId());
            UUID zone = school == null ? i.getZoneId() : school.zoneId();
            boolean decider = access.director(caller) || (scope != null && access.zoneManagerOf(caller, scope, i.getSchoolId(), zone));
            if (decider) {
                items.add(new PendingItem(
                        i.getId(),
                        i.getSchoolId(),
                        school == null ? "Unknown School" : school.name(),
                        i.getEarnerUserId(),
                        names.getOrDefault(i.getEarnerManagerId(), "Unknown"),
                        i.getAmount(),
                        i.getClosureDate()));
            }
        }
        return items;
    }
}
