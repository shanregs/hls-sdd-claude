package com.hls.reporting.api;

import com.hls.identity.permissions.PermissionAction;

/**
 * What a module offers as one tile (data, not code). {@code permissionModule} names a
 * {@link com.hls.identity.permissions.PermissionModule} constant; the caller needs {@code action} on it for the tile to be
 * listed or read.
 */
public record TileDefinition(
        String key,
        Area area,
        int order,
        String label,
        PeriodKind periodKind,
        String permissionModule,
        PermissionAction action,
        ScopeKind scope,
        boolean zoneAware,
        Link link) {}
