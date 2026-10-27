package com.hls.reporting.api;

import com.hls.identity.permissions.PermissionModule;
import java.util.Set;

/**
 * What a report is: its key, the permission module whose View and Export grants gate it, the filters it offers and whether
 * it has a month state and a CSV export.
 */
public record ReportDefinition(
        String key,
        String title,
        PermissionModule module,
        Set<ReportFilter> filters,
        boolean hasMonthState,
        boolean exportAllowed) {

    public ReportDefinition {
        filters = Set.copyOf(filters);
    }
}
