package com.hls.reporting.api;

import java.util.Set;
import java.util.UUID;

/**
 * What the caller may see, resolved once per request before any counting (spec 014a FR-006). {@code orgWide} for Admin
 * and Director; otherwise the Zones and Schools of a Manager. A caller with neither is {@code ownOnly}: a Teacher, who
 * sees only their own rows (the source finds the Teacher from {@link ReportContext#userId()}).
 */
public record ReportScope(boolean orgWide, Set<UUID> zoneIds, Set<UUID> schoolIds) {

    public ReportScope {
        zoneIds = Set.copyOf(zoneIds);
        schoolIds = Set.copyOf(schoolIds);
    }

    public boolean ownOnly() {
        return !orgWide && zoneIds.isEmpty() && schoolIds.isEmpty();
    }

    public boolean allowsZone(UUID zoneId) {
        return orgWide || zoneIds.contains(zoneId);
    }

    public boolean allowsSchool(UUID schoolId, UUID zoneOfSchool) {
        return orgWide || schoolIds.contains(schoolId) || (zoneOfSchool != null && zoneIds.contains(zoneOfSchool));
    }
}
