package com.hls.activitycalendar.internal;

import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.school.api.SchoolDirectory;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Names, the Manager flag and the Zone of the owners and attendees of the items, looked up in bulk (the number of
 * queries follows the number of people, never the number of items).
 */
@Component
public class PeopleDirectory {

    /** A snapshot for one request. Built by {@link PeopleDirectory#load} or directly in tests. */
    public static final class People {

        private final Map<UUID, String> names;
        private final Set<UUID> managers;
        private final Map<UUID, ZoneRef> primaryZone;

        public People(Map<UUID, String> names, Set<UUID> managers, Map<UUID, ZoneRef> primaryZone) {
            this.names = names;
            this.managers = managers;
            this.primaryZone = primaryZone;
        }

        public boolean isManager(UUID userId) {
            return managers.contains(userId);
        }

        public String name(UUID userId) {
            return names.getOrDefault(userId, "Unknown user");
        }

        /** The Manager's current Zone; with several Zones the first by name then id, so the pick is stable. Null for none. */
        public ZoneRef zoneOf(UUID userId) {
            return primaryZone.get(userId);
        }

        public Set<UUID> managerUserIds() {
            return managers;
        }
    }

    private final ManagerQueries managerQueries;
    private final ScopeQueries scopes;
    private final SchoolDirectory schools;
    private final UserAdminService users;

    public PeopleDirectory(ManagerQueries managerQueries, ScopeQueries scopes, SchoolDirectory schools, UserAdminService users) {
        this.managerQueries = managerQueries;
        this.scopes = scopes;
        this.schools = schools;
        this.users = users;
    }

    /** Every Manager (active or not, so a deactivated Manager's past items still resolve) and the given users. */
    public People load(Collection<UUID> userIds) {
        Map<UUID, ManagerRef> managerByUser = new HashMap<>();
        for (ManagerRef m : managerQueries.allManagers()) {
            managerByUser.put(m.userId(), m);
        }
        Map<UUID, String> names = new HashMap<>();
        managerByUser.forEach((id, m) -> names.put(id, m.displayName()));
        Set<UUID> others = userIds.stream().filter(id -> !managerByUser.containsKey(id)).collect(Collectors.toSet());
        if (!others.isEmpty()) {
            users.findWithoutRoles(others).forEach((id, u) -> names.put(id, u.user().getDisplayName()));
        }
        Set<UUID> involvedManagers = userIds.stream().filter(managerByUser::containsKey).collect(Collectors.toSet());
        Map<UUID, Set<UUID>> zoneIdsByManager = new HashMap<>();
        Set<UUID> allZoneIds = new HashSet<>();
        for (UUID userId : involvedManagers) {
            Set<UUID> zoneIds = scopes.scopeOf(userId, Set.of(Role.MANAGER)).zoneIds();
            zoneIdsByManager.put(userId, zoneIds);
            allZoneIds.addAll(zoneIds);
        }
        Map<UUID, String> zoneNames = allZoneIds.isEmpty()
                ? Map.of()
                : schools.zones(allZoneIds).stream().collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<UUID, ZoneRef> primary = new HashMap<>();
        zoneIdsByManager.forEach((userId, zoneIds) -> {
            List<ZoneRef> refs = zoneIds.stream()
                    .filter(zoneNames::containsKey)
                    .map(z -> new ZoneRef(z, zoneNames.get(z)))
                    .sorted(Comparator.comparing(ZoneRef::name).thenComparing(ZoneRef::id))
                    .toList();
            if (!refs.isEmpty()) {
                primary.put(userId, refs.get(0));
            }
        });
        return new People(names, managerByUser.keySet(), primary);
    }
}
