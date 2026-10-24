package com.hls.reporting.api;

import com.hls.identity.permissions.PermissionAction;

/**
 * What a module offers as one tile (data, not code). {@code permissionModule} names a
 * {@link com.hls.identity.permissions.PermissionModule} constant; the caller needs {@code action} on it for the tile to be
 * listed or read.
 *
 * <p>{@code ownRowModule} (optional) names a second module that also lets a caller see the tile, but only their own row: a
 * caller who holds it and not the main grant (for example a Manager with their own attendance) gets the tile scoped to
 * themselves. The tile's source must narrow to the caller whenever the caller is not org-wide. Granting it never opens the
 * owning module's own endpoints.
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
        Link link,
        String ownRowModule,
        PermissionAction ownRowAction) {

    /** A tile with no own-row alternative. */
    public TileDefinition(
            String key,
            Area area,
            int order,
            String label,
            PeriodKind periodKind,
            String permissionModule,
            PermissionAction action,
            ScopeKind scope,
            boolean zoneAware,
            Link link) {
        this(key, area, order, label, periodKind, permissionModule, action, scope, zoneAware, link, null, null);
    }
}
