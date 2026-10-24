package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileSource;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Collects every {@link TileSource} the owning modules provide and applies the caller's grants (spec 028 FR-013, FR-015).
 * A tile the caller may not see is never listed and never read, so its figure is never serialized.
 */
@Component
public class TileRegistry {

    /** A tile together with the source that reads it. */
    public record Entry(TileSource source, TileDefinition definition) {}

    private final PermissionGuard guard;
    private final List<Entry> entries;
    private final Map<String, Entry> byKey;

    public TileRegistry(ObjectProvider<TileSource> sources, PermissionGuard guard) {
        this.guard = guard;
        Map<String, Entry> keyed = new LinkedHashMap<>();
        for (TileSource source : sources.orderedStream().toList()) {
            for (TileDefinition definition : source.tiles()) {
                // A grant naming a module that does not exist is a programming error: fail at start-up, not per request.
                PermissionModule.valueOf(definition.permissionModule());
                if (definition.ownRowModule() != null) {
                    PermissionModule.valueOf(definition.ownRowModule());
                }
                if (keyed.put(definition.key(), new Entry(source, definition)) != null) {
                    throw new IllegalStateException("Duplicate dashboard tile key: " + definition.key());
                }
            }
        }
        this.byKey = Map.copyOf(keyed);
        this.entries = keyed.values().stream()
                .sorted(Comparator.comparing((Entry e) -> e.definition().area())
                        .thenComparingInt(e -> e.definition().order())
                        .thenComparing(e -> e.definition().key()))
                .toList();
    }

    /** True for a caller who holds only the Manager role among Admin, Director and Manager. */
    static boolean managerOnly(Set<Role> roles) {
        return roles.contains(Role.MANAGER) && !roles.contains(Role.ADMIN) && !roles.contains(Role.DIRECTOR);
    }

    private boolean visible(Entry entry, Set<Role> roles) {
        TileDefinition d = entry.definition();
        if (d.scope() == ScopeKind.ORG_WIDE_ONLY && managerOnly(roles)) {
            return false;
        }
        if (guard.isGranted(roles, PermissionModule.valueOf(d.permissionModule()), d.action())) {
            return true;
        }
        return d.ownRowModule() != null
                && guard.isGranted(roles, PermissionModule.valueOf(d.ownRowModule()), d.ownRowAction());
    }

    /** The caller's tiles, ordered by area then {@code order}; a key appears once however many roles the caller holds. */
    public List<Entry> manifestFor(Set<Role> roles) {
        Map<String, Entry> seen = new HashMap<>();
        return entries.stream()
                .filter(e -> visible(e, roles))
                .filter(e -> seen.putIfAbsent(e.definition().key(), e) == null)
                .toList();
    }

    public List<Entry> manifestFor(Set<Role> roles, Area area) {
        return manifestFor(roles).stream().filter(e -> e.definition().area() == area).toList();
    }

    public Optional<Entry> find(Set<Role> roles, Area area, String key) {
        return manifestFor(roles, area).stream().filter(e -> e.definition().key().equals(key)).findFirst();
    }
}
