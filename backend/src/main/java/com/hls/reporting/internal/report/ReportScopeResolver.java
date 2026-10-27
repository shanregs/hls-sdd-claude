package com.hls.reporting.internal.report;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportOutOfScopeException;
import com.hls.reporting.api.ReportRequest;
import com.hls.reporting.api.ReportScope;
import com.hls.reporting.internal.DashboardInvalidInputException;
import com.hls.school.api.SchoolDirectory;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the caller's scope once per request (Admin and Director org-wide, a Manager through their Zones and Schools, a
 * Teacher own) and refuses a Zone or School filter outside it (spec 014a FR-006). Teacher and Manager filters are checked by
 * the report source, which knows how to find them.
 */
@Component
public class ReportScopeResolver {

    private final ScopeQueries scopeQueries;
    private final SchoolDirectory directory;

    public ReportScopeResolver(ScopeQueries scopeQueries, SchoolDirectory directory) {
        this.scopeQueries = scopeQueries;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public ReportScope scopeOf(UUID userId, Set<Role> roles) {
        ScopeView view = scopeQueries.scopeOf(userId, roles);
        return new ReportScope(view.orgWide(), view.zoneIds(), view.schoolIds());
    }

    /** Parses the filters; a Zone or School outside {@code scope} is refused, an unknown id is invalid. */
    @Transactional(readOnly = true)
    public ReportRequest request(
            ReportScope scope,
            Set<ReportFilter> offered,
            YearMonth month,
            String zone,
            String school,
            String teacher,
            String manager) {
        return request(scope, offered, month, zone, school, teacher, manager, null);
    }

    /** As above, with the season filter (spec 014c A0); a season is free text, trimmed, and refused when the report has no season filter. */
    @Transactional(readOnly = true)
    public ReportRequest request(
            ReportScope scope,
            Set<ReportFilter> offered,
            YearMonth month,
            String zone,
            String school,
            String teacher,
            String manager,
            String season) {
        String seasonText = null;
        if (season != null && !season.isBlank()) {
            if (!offered.contains(ReportFilter.SEASON)) {
                throw new DashboardInvalidInputException("This report has no season filter.");
            }
            seasonText = season.trim();
            if (seasonText.length() > 100) {
                throw new DashboardInvalidInputException("The season filter is not valid.");
            }
        }
        UUID zoneId = filter(ReportFilter.ZONE, offered, zone);
        UUID schoolId = filter(ReportFilter.SCHOOL, offered, school);
        UUID teacherId = filter(ReportFilter.TEACHER, offered, teacher);
        UUID managerId = filter(ReportFilter.MANAGER, offered, manager);
        if (zoneId != null && (directory.zone(zoneId).isEmpty() || !scope.allowsZone(zoneId))) {
            throw new ReportOutOfScopeException("That Zone is outside your area.");
        }
        if (schoolId != null) {
            var info = directory.school(schoolId);
            if (info.isEmpty() || !scope.allowsSchool(schoolId, info.get().zoneId())) {
                throw new ReportOutOfScopeException("That School is outside your area.");
            }
            if (zoneId != null && !zoneId.equals(info.get().zoneId())) {
                throw new DashboardInvalidInputException("That School is not in the chosen Zone.");
            }
        }
        return new ReportRequest(month, zoneId, schoolId, teacherId, managerId, seasonText);
    }

    /** Parses a month written as YYYY-MM. */
    public static YearMonth month(String text) {
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new DashboardInvalidInputException("The month must be written as YYYY-MM.");
        }
    }

    private static UUID filter(ReportFilter which, Set<ReportFilter> offered, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (!offered.contains(which)) {
            throw new DashboardInvalidInputException("This report has no " + which.name().toLowerCase() + " filter.");
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            throw new DashboardInvalidInputException("The " + which.name().toLowerCase() + " filter is not valid.");
        }
    }
}
