package com.hls.organization.api;

import java.util.Set;
import java.util.UUID;

/**
 * What a caller may access (Constitution Principle III): {@code orgWide} for Admin/Director,
 * otherwise the Zones and Schools of an active Manager; empty for everyone else.
 */
public record ScopeView(boolean orgWide, Set<UUID> zoneIds, Set<UUID> schoolIds) {

    public static ScopeView everything() {
        return new ScopeView(true, Set.of(), Set.of());
    }

    public static ScopeView none() {
        return new ScopeView(false, Set.of(), Set.of());
    }

    public boolean allowsSchool(UUID schoolId) {
        return orgWide || schoolIds.contains(schoolId);
    }
}
