package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.activitycalendar.internal.CalendarEntry;
import com.hls.activitycalendar.internal.CalendarZoneResolver;
import com.hls.activitycalendar.internal.PeopleDirectory;
import com.hls.activitycalendar.internal.PeopleDirectory.People;
import com.hls.activitycalendar.internal.ZoneRef;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.recruitment.api.ProspectZones;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T028 (FR-014): own Zone first, then the owner's current Zone, else none. */
class CalendarZoneResolverTest {

    private static final UUID OWN_ZONE = UUID.randomUUID();
    private static final UUID SCHOOL_ZONE = UUID.randomUUID();
    private static final UUID PROSPECT_ZONE = UUID.randomUUID();
    private static final UUID OWNER_ZONE = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SCHOOL = UUID.randomUUID();
    private static final UUID PROSPECT = UUID.randomUUID();

    private final SchoolDirectory schools = mock(SchoolDirectory.class);
    private final ProspectZones prospectZones = mock(ProspectZones.class);
    private final CalendarZoneResolver resolver = new CalendarZoneResolver(prospectZones, schools);
    private final People people =
            new People(Map.of(OWNER, "Owner"), Set.of(OWNER), Map.of(OWNER, new ZoneRef(OWNER_ZONE, "Owner Zone")));

    CalendarZoneResolverTest() {
        when(schools.zones(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> new ZoneInfo(id, id.equals(OWN_ZONE) ? "Own" : id.equals(SCHOOL_ZONE) ? "School Zone" : "Prospect Zone"))
                    .toList();
        });
        when(schools.schools(anyCollection()))
                .thenReturn(List.of(new SchoolInfo(SCHOOL, "School", UUID.randomUUID(), SCHOOL_ZONE, true)));
        when(prospectZones.zonesOf(anyCollection())).thenReturn(Map.of(PROSPECT, PROSPECT_ZONE));
    }

    private static CalendarEntry entry(UUID zone, UUID schoolId, String linkedType, UUID linkedId, UUID owner) {
        return new CalendarEntry(
                "TASK", null, UUID.randomUUID(), LocalDate.of(2026, 10, 5), null, null, owner, Set.of(), null, zone, schoolId, linkedType,
                linkedId, "ASSIGNED", null);
    }

    @Test
    void theItemsOwnZoneWinsOverTheOwnersZone() {
        CalendarEntry e = entry(OWN_ZONE, null, null, null, OWNER);

        ZoneRef zone = resolver.resolve(List.of(e), people).zoneOf(e);

        assertThat(zone).isEqualTo(new ZoneRef(OWN_ZONE, "Own"));
    }

    @Test
    void aLinkedSchoolOrProspectGivesItsZone() {
        CalendarEntry bySchool = entry(null, null, "SCHOOL", SCHOOL, OWNER);
        CalendarEntry byProspect = entry(null, null, "PROSPECT", PROSPECT, OWNER);
        CalendarEntry accountVisit = entry(null, SCHOOL, null, null, OWNER);

        var resolution = resolver.resolve(List.of(bySchool, byProspect, accountVisit), people);

        assertThat(resolution.zoneOf(bySchool)).isEqualTo(new ZoneRef(SCHOOL_ZONE, "School Zone"));
        assertThat(resolution.zoneOf(byProspect)).isEqualTo(new ZoneRef(PROSPECT_ZONE, "Prospect Zone"));
        assertThat(resolution.zoneOf(accountVisit)).isEqualTo(new ZoneRef(SCHOOL_ZONE, "School Zone"));
    }

    @Test
    void withoutAZoneOfItsOwnTheOwnersCurrentZoneIsUsed() {
        CalendarEntry e = entry(null, null, "INVOICE", UUID.randomUUID(), OWNER);

        assertThat(resolver.resolve(List.of(e), people).zoneOf(e)).isEqualTo(new ZoneRef(OWNER_ZONE, "Owner Zone"));
    }

    @Test
    void anItemWithNoZoneAnywhereHasNone() {
        UUID stranger = UUID.randomUUID();
        CalendarEntry e = entry(null, null, null, null, stranger);

        assertThat(resolver.resolve(List.of(e), people).zoneOf(e)).isNull();
    }

    @Test
    void aManagerWithSeveralZonesGetsTheSameZoneEveryTime() {
        UUID zoneB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        UUID zoneA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID manager = UUID.randomUUID();
        ManagerQueries managers = mock(ManagerQueries.class);
        when(managers.allManagers()).thenReturn(List.of(new ManagerRef(UUID.randomUUID(), manager, "Mina", true)));
        ScopeQueries scopes = mock(ScopeQueries.class);
        when(scopes.scopeOf(manager, Set.of(Role.MANAGER))).thenReturn(new ScopeView(false, Set.of(zoneB, zoneA), Set.of()));
        SchoolDirectory directoryZones = mock(SchoolDirectory.class);
        when(directoryZones.zones(anyCollection())).thenReturn(List.of(new ZoneInfo(zoneB, "Same"), new ZoneInfo(zoneA, "Same")));
        PeopleDirectory directory = new PeopleDirectory(managers, scopes, directoryZones, mock(UserAdminService.class));

        for (int i = 0; i < 5; i++) {
            People loaded = directory.load(Set.of(manager));
            assertThat(loaded.zoneOf(manager).id()).isEqualTo(zoneA);
            assertThat(loaded.isManager(manager)).isTrue();
            assertThat(loaded.name(manager)).isEqualTo("Mina");
        }
    }
}
