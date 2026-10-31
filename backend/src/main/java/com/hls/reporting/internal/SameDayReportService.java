package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ScopeQueries;
import com.hls.reporting.api.ReportOutOfScopeException;
import com.hls.reporting.api.SameDayCountSource;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The same-day report (spec 040 US4, FR-014): for a month, how many leave days, Manager covers, tasks and visits were recorded
 * for the day without prior approval, per Manager and per School. It owns no data: each module counts its own records in the
 * caller's scope, and a section the caller may not view is left out (not shown empty).
 */
@Service
public class SameDayReportService {

    public record Row(UUID id, String name, BigDecimal count) {}

    public record Section(List<Row> byManager, List<Row> bySchool) {}

    private final List<SameDayCountSource> sources;
    private final PermissionGuard guard;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final AppUserRepository users;
    private final ScopeQueries scopeQueries;

    public SameDayReportService(
            List<SameDayCountSource> sources,
            PermissionGuard guard,
            SchoolDirectory schools,
            ManagerQueries managers,
            AppUserRepository users,
            ScopeQueries scopeQueries) {
        this.sources = sources;
        this.guard = guard;
        this.schools = schools;
        this.managers = managers;
        this.users = users;
        this.scopeQueries = scopeQueries;
    }

    @Transactional(readOnly = true)
    public Map<String, Section> report(UUID userId, Set<Role> roles, YearMonth month) {
        return report(userId, roles, month, null);
    }

    /**
     * As above, narrowed to one Zone when {@code zoneId} is given: only that Zone's Schools and Managers are counted. A Zone
     * that does not exist or is outside the caller's scope is refused (403), so no figures leak.
     */
    @Transactional(readOnly = true)
    public Map<String, Section> report(UUID userId, Set<Role> roles, YearMonth month, UUID zoneId) {
        Set<UUID> zoneSchools = null;
        Set<UUID> zoneManagerUsers = null;
        if (zoneId != null) {
            var scope = scopeQueries.scopeOf(userId, roles);
            boolean allowed = scope.orgWide() || scope.zoneIds().contains(zoneId);
            if (!allowed || schools.zone(zoneId).isEmpty()) {
                throw new ReportOutOfScopeException("That Zone is outside your area.");
            }
            Set<UUID> inZone = new java.util.HashSet<>();
            schools.schoolsInZone(zoneId).forEach(s -> inZone.add(s.id()));
            zoneSchools = inZone;
            Set<UUID> managerUsers = new java.util.HashSet<>();
            for (var m : managers.managersOfZone(zoneId)) {
                managerUsers.add(m.userId());
            }
            zoneManagerUsers = managerUsers;
        }
        Map<String, Section> sections = new LinkedHashMap<>();
        for (SameDayCountSource source : sources) {
            if (!guard.isGranted(roles, source.permission(), PermissionAction.VIEW)) {
                continue;
            }
            SameDayCountSource.Counts counts = source.counts(month, userId, roles);
            boolean derive = counts.byManagerUser().isEmpty();
            sections.put(source.key(), section(narrow(counts, zoneSchools, zoneManagerUsers), derive));
        }
        if (sections.isEmpty()) {
            throw new DashboardForbiddenException("You cannot see this report.");
        }
        return sections;
    }

    /** Keeps only the Zone's Schools and Managers; a School-only count still turns into its Manager's count afterwards. */
    private static SameDayCountSource.Counts narrow(
            SameDayCountSource.Counts counts, Set<UUID> zoneSchools, Set<UUID> zoneManagerUsers) {
        if (zoneSchools == null) {
            return counts;
        }
        Map<UUID, BigDecimal> bySchool = new HashMap<>(counts.bySchool());
        bySchool.keySet().retainAll(zoneSchools);
        Map<UUID, BigDecimal> byManager = new HashMap<>(counts.byManagerUser());
        byManager.keySet().retainAll(zoneManagerUsers);
        return new SameDayCountSource.Counts(bySchool, byManager);
    }

    private Section section(SameDayCountSource.Counts counts, boolean derive) {
        Map<UUID, BigDecimal> byManager = new HashMap<>(counts.byManagerUser());
        // a School-based count with no Manager grouping is the School's Manager's count
        if (derive) {
            counts.bySchool().forEach((schoolId, n) -> managers.managerOfSchool(schoolId)
                    .ifPresent(m -> byManager.merge(m.userId(), n, BigDecimal::add)));
        }
        Map<UUID, String> userNames = new HashMap<>();
        users.findAllById(byManager.keySet()).forEach(u -> userNames.put(u.getId(), nameOf(u)));
        Map<UUID, String> schoolNames = new HashMap<>();
        if (!counts.bySchool().isEmpty()) {
            schools.schools(counts.bySchool().keySet()).forEach(s -> schoolNames.put(s.id(), s.name()));
        }
        return new Section(
                rows(byManager, userNames), rows(counts.bySchool(), schoolNames));
    }

    private static String nameOf(AppUser user) {
        return user.getDisplayName();
    }

    private static List<Row> rows(Map<UUID, BigDecimal> counts, Map<UUID, String> names) {
        List<Row> rows = new ArrayList<>();
        counts.forEach((id, n) -> rows.add(new Row(id, names.getOrDefault(id, "-"), n)));
        rows.sort(Comparator.comparing(Row::count).reversed().thenComparing(Row::name));
        return rows;
    }
}
