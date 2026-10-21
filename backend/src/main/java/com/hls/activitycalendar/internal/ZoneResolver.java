package com.hls.activitycalendar.internal;

import com.hls.activitycalendar.internal.PeopleDirectory.People;
import com.hls.recruitment.api.ProspectZones;
import com.hls.school.api.SchoolDirectory;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Finds the Zone of each item (FR-014): the item's own Zone (a prospect's Zone, a School's Zone, or the Zone of the
 * School or prospect it is linked to) first, then the owner's current Zone, else none ("No Zone"). Bulk lookups only.
 */
@Component
public class ZoneResolver {

    /** The Zone of each entry, by entry; null when it has none. */
    public static final class Resolution {

        private final Map<CalendarEntry, ZoneRef> zones;

        Resolution(Map<CalendarEntry, ZoneRef> zones) {
            this.zones = zones;
        }

        public ZoneRef zoneOf(CalendarEntry entry) {
            return zones.get(entry);
        }
    }

    private final ProspectZones prospectZones;
    private final SchoolDirectory schools;

    public ZoneResolver(ProspectZones prospectZones, SchoolDirectory schools) {
        this.prospectZones = prospectZones;
        this.schools = schools;
    }

    public Resolution resolve(Collection<CalendarEntry> entries, People people) {
        Set<UUID> schoolIds = new HashSet<>();
        Set<UUID> prospectIds = new HashSet<>();
        for (CalendarEntry e : entries) {
            if (e.zoneId() != null) {
                continue;
            }
            if (e.schoolId() != null) {
                schoolIds.add(e.schoolId());
            }
            if (e.linkedId() != null && "SCHOOL".equals(e.linkedType())) {
                schoolIds.add(e.linkedId());
            }
            if (e.linkedId() != null && "PROSPECT".equals(e.linkedType())) {
                prospectIds.add(e.linkedId());
            }
        }
        Map<UUID, UUID> zoneOfSchool = schoolIds.isEmpty()
                ? Map.of()
                : schools.schools(schoolIds).stream()
                        .filter(s -> s.zoneId() != null)
                        .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::zoneId));
        Map<UUID, UUID> zoneOfProspect = prospectIds.isEmpty() ? Map.of() : prospectZones.zonesOf(prospectIds);

        Map<CalendarEntry, UUID> ownZone = new HashMap<>();
        Map<CalendarEntry, ZoneRef> ownerZone = new HashMap<>();
        Set<UUID> needNames = new HashSet<>();
        for (CalendarEntry e : entries) {
            UUID zone = e.zoneId();
            if (zone == null && e.linkedId() != null && "SCHOOL".equals(e.linkedType())) {
                zone = zoneOfSchool.get(e.linkedId());
            }
            if (zone == null && e.linkedId() != null && "PROSPECT".equals(e.linkedType())) {
                zone = zoneOfProspect.get(e.linkedId());
            }
            if (zone == null && e.schoolId() != null) {
                zone = zoneOfSchool.get(e.schoolId());
            }
            if (zone != null) {
                ownZone.put(e, zone);
                needNames.add(zone);
            } else {
                ZoneRef owner = e.ownerUserId() == null ? null : people.zoneOf(e.ownerUserId());
                if (owner != null) {
                    ownerZone.put(e, owner);
                }
            }
        }
        Map<UUID, String> names = needNames.isEmpty()
                ? Map.of()
                : schools.zones(needNames).stream().collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<CalendarEntry, ZoneRef> out = new HashMap<>(ownerZone);
        ownZone.forEach((e, id) -> {
            String name = names.get(id);
            if (name != null) {
                out.put(e, new ZoneRef(id, name));
            }
        });
        return new Resolution(out);
    }
}
