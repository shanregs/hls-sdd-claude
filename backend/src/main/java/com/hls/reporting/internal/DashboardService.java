package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.TileResult;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Builds the manifest and reads areas and tiles for a caller (spec 028 contracts/dashboard-api.md). Stores nothing. */
@Service("managementDashboardService")
public class DashboardService {

    public record TileView(String key, String label, PeriodKind periodKind, boolean zoneAware, Link link) {}

    public record AreaView(String area, String title, List<TileView> tiles) {}

    public record ZoneOption(UUID id, String name) {}

    public record Filters(List<ZoneOption> zones, String defaultPeriod) {}

    public record Manifest(List<AreaView> areas, Filters filters, boolean inbox) {}

    public record AreaResponse(String area, Instant takenAt, List<TileResult> tiles) {}

    private final TileRegistry registry;
    private final AreaReader reader;
    private final ZoneResolver zones;
    private final PermissionGuard guard;
    private final ObjectProvider<ApprovalSource> approvalSources;
    private final Clock clock;
    private final ManagerComparisonService comparison;
    private final InboxService inbox;

    public DashboardService(
            TileRegistry registry,
            AreaReader reader,
            ZoneResolver zones,
            PermissionGuard guard,
            ObjectProvider<ApprovalSource> approvalSources,
            Clock clock,
            ManagerComparisonService comparison,
            InboxService inbox) {
        this.registry = registry;
        this.reader = reader;
        this.zones = zones;
        this.guard = guard;
        this.approvalSources = approvalSources;
        this.clock = clock;
        this.comparison = comparison;
        this.inbox = inbox;
    }

    /** Definitions only, no figures. Teacher and System hold none of the grants, so theirs is empty. */
    public Manifest manifest(UUID userId, Set<Role> roles) {
        Map<Area, List<TileView>> byArea = new EnumMap<>(Area.class);
        for (TileRegistry.Entry entry : registry.manifestFor(roles)) {
            var d = entry.definition();
            byArea.computeIfAbsent(d.area(), a -> new ArrayList<>())
                    .add(new TileView(d.key(), d.label(), d.periodKind(), d.zoneAware(), d.link()));
        }
        List<AreaView> areas = new ArrayList<>();
        byArea.forEach((area, tiles) -> areas.add(new AreaView(area.name(), area.title(), List.copyOf(tiles))));
        boolean anyTile = !areas.isEmpty();
        List<ZoneOption> pickable = anyTile
                ? zones.pickableZones(userId, roles).stream()
                        .map(z -> new ZoneOption(z.id(), z.name()))
                        .toList()
                : List.of();
        String defaultPeriod = anyTile ? YearMonth.from(zones.today()).toString() : null;
        return new Manifest(areas, new Filters(pickable, defaultPeriod), inboxAvailable(roles));
    }

    private boolean inboxAvailable(Set<Role> roles) {
        return inbox.available(roles);
    }

    /** Every tile of one area, computed now; an area outside the caller's manifest answers 404. */
    public AreaResponse area(UUID userId, Set<Role> roles, String areaName, String zoneId, String period) {
        Area area = parseArea(areaName);
        List<TileRegistry.Entry> entries = registry.manifestFor(roles, area);
        if (entries.isEmpty()) {
            throw new DashboardNotFoundException("Area not found.");
        }
        var ctx = context(userId, roles, zoneId, period);
        List<TileResult> results = reader.read(entries, ctx);
        Instant takenAt = results.isEmpty() ? Instant.now(clock) : results.get(0).takenAt();
        return new AreaResponse(area.name(), takenAt, results);
    }

    /** One tile again, the retry after an error. */
    public TileResult tile(UUID userId, Set<Role> roles, String areaName, String key, String zoneId, String period) {
        Area area = parseArea(areaName);
        Optional<TileRegistry.Entry> entry = registry.find(roles, area, key);
        if (entry.isEmpty()) {
            throw new DashboardNotFoundException("Tile not found.");
        }
        return reader.readOne(entry.get(), context(userId, roles, zoneId, period));
    }

    /** The Manager-by-Manager table, sorted; Admin and Director only (a Manager-only caller gets 403). */
    public TileResult managers(UUID userId, Set<Role> roles, String zoneId, String period, String sort, String dir) {
        if (TileRegistry.managerOnly(roles)) {
            throw new DashboardForbiddenException("The Manager comparison is for Admin and Director.");
        }
        if (registry.find(roles, Area.MANAGERS, ManagerComparisonService.KEY).isEmpty()) {
            throw new DashboardNotFoundException("Tile not found.");
        }
        return comparison.compare(context(userId, roles, zoneId, period), sort, dir);
    }

    private RequestTileContext context(UUID userId, Set<Role> roles, String zoneId, String period) {
        Optional<UUID> zone = zones.zone(zoneId, userId, roles);
        YearMonth month = zones.period(period);
        return new RequestTileContext(userId, roles, zones.today(), month, zone);
    }

    private static Area parseArea(String name) {
        try {
            return Area.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DashboardNotFoundException("Area not found.");
        }
    }
}
