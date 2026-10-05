package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.school.api.SchoolRegistry;
import com.hls.support.MasterDataTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 023 small interfaces: a School created for another module, and the Managers of a Zone. */
class SchoolRegistryTest extends MasterDataTestBase {

    @Autowired
    SchoolRegistry registry;

    @Autowired
    ManagerQueries managerQueries;

    @Test
    void aSchoolCreatedThroughTheRegistryIsAnOrdinarySchoolOfThePlaceAndIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        UUID actor = signInAs(Role.ADMIN).userId();
        UUID zone = zone(admin);
        UUID place = place(admin, zone);
        String name = uniqueName("Won School");

        UUID id = registry.create(actor, place, new SchoolRegistry.NewSchool(name, "1 Main Road", "Mrs Rao", "9111111111", "accounts@won.test"));

        Resp view = get("/api/v1/schools/" + id, admin);
        assertThat(view.status()).isEqualTo(200);
        assertThat(view.body()).contains(name, "1 Main Road", "accounts@won.test");
        assertThat(registry.findByNameInPlace(place, name.toUpperCase())).contains(id);
        assertThat(registry.findByNameInPlace(place, "Nobody")).isEmpty();
        assertThat(registry.findByNameInPlace(place(admin, zone), name)).isEmpty();
        assertChangeRecorded(admin, "SCHOOL", id, "created");
    }

    @Test
    void managersOfZoneListsTheActiveManagersAssignedToIt() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        UUID otherZone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);

        assertThat(managerQueries.managersOfZone(zone)).extracting(ManagerQueries.ManagerRef::id).containsExactly(manager.managerId());
        assertThat(managerQueries.managersOfZone(otherZone)).isEmpty();
        assertThat(managerQueries.managersOfZone(zone).get(0).userId()).isEqualTo(manager.signed().userId());
    }
}
