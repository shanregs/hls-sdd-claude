package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
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

    public SameDayReportService(
            List<SameDayCountSource> sources,
            PermissionGuard guard,
            SchoolDirectory schools,
            ManagerQueries managers,
            AppUserRepository users) {
        this.sources = sources;
        this.guard = guard;
        this.schools = schools;
        this.managers = managers;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<String, Section> report(UUID userId, Set<Role> roles, YearMonth month) {
        Map<String, Section> sections = new LinkedHashMap<>();
        for (SameDayCountSource source : sources) {
            if (!guard.isGranted(roles, source.permission(), PermissionAction.VIEW)) {
                continue;
            }
            sections.put(source.key(), section(source.counts(month, userId, roles)));
        }
        if (sections.isEmpty()) {
            throw new DashboardForbiddenException("You cannot see this report.");
        }
        return sections;
    }

    private Section section(SameDayCountSource.Counts counts) {
        Map<UUID, BigDecimal> byManager = new HashMap<>(counts.byManagerUser());
        // a School-based count with no Manager grouping is the School's Manager's count
        if (byManager.isEmpty()) {
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
