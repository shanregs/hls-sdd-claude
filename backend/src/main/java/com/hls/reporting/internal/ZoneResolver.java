package com.hls.reporting.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Validates the Zone and period a request asks for, and supplies the business date (Asia/Kolkata). */
@Component
public class ZoneResolver {

    private final ScopeQueries scopeQueries;
    private final SchoolDirectory directory;
    private final BusinessDate businessDate;

    public ZoneResolver(ScopeQueries scopeQueries, SchoolDirectory directory, BusinessDate businessDate) {
        this.scopeQueries = scopeQueries;
        this.directory = directory;
        this.businessDate = businessDate;
    }

    public LocalDate today() {
        return businessDate.today();
    }

    /** The month asked for, or the current month by business date when none is given. */
    public YearMonth period(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.from(today());
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw new DashboardInvalidInputException("The period must be a month written as YYYY-MM.");
        }
    }

    /** The Zone asked for, checked against the caller's scope; an unknown or unreachable Zone is a 404. */
    @Transactional(readOnly = true)
    public Optional<UUID> zone(String zoneId, UUID userId, Set<Role> roles) {
        if (zoneId == null || zoneId.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(zoneId.trim());
        } catch (IllegalArgumentException e) {
            throw new DashboardNotFoundException("Zone not found.");
        }
        ScopeView scope = scopeQueries.scopeOf(userId, roles);
        boolean reachable = scope.orgWide() || scope.zoneIds().contains(id);
        if (!reachable || directory.zone(id).isEmpty()) {
            throw new DashboardNotFoundException("Zone not found.");
        }
        return Optional.of(id);
    }

    /** The Zones the caller may pick in the filter. */
    @Transactional(readOnly = true)
    public List<ZoneInfo> pickableZones(UUID userId, Set<Role> roles) {
        ScopeView scope = scopeQueries.scopeOf(userId, roles);
        if (scope.orgWide()) {
            return directory.allZones();
        }
        return directory.zones(scope.zoneIds()).stream()
                .sorted(java.util.Comparator.comparing(ZoneInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
