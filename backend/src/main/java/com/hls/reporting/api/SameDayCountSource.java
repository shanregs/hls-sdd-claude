package com.hls.reporting.api;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a module tells the same-day report (spec 040 US4): how many of its records in a month were recorded for the day
 * without prior approval or planning, counted in the caller's scope. {@code reporting} owns no data; it asks each module that
 * registers one of these, and shows only the modules the caller may view.
 */
public interface SameDayCountSource {

    /**
     * Counts by School and by the Manager's user. Either map may be empty when the module has no such grouping: a School-based
     * count is turned into a per-Manager count by the report (the School's Manager).
     */
    record Counts(Map<UUID, BigDecimal> bySchool, Map<UUID, BigDecimal> byManagerUser) {

        public static Counts none() {
            return new Counts(Map.of(), Map.of());
        }
    }

    /** The section name in the report: leave, covers, tasks or visits. */
    String key();

    /** The View right the caller needs to see this section. */
    PermissionModule permission();

    Counts counts(YearMonth month, UUID callerUserId, Set<Role> callerRoles);
}
