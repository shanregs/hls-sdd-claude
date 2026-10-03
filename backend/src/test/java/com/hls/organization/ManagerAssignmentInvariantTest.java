package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** User Story 3 (FR-009/FR-010): the School's Manager always covers its Zone, atomically, with history. */
class ManagerAssignmentInvariantTest extends MasterDataTestBase {

    @Test
    void assigningAManagerWhoDoesNotCoverTheZoneIsRefusedWithNoChange() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin); // zone, place, school
        UUID otherZone = zone(admin);
        ManagerCtx outsider = newManager(admin, otherZone);

        Resp refused = assignSchoolManagerRaw(admin, ids[2], outsider.managerId());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("does not cover the School's Zone");
        assertThat(get("/api/v1/schools/" + ids[2], admin).body()).contains("\"needsManager\":true");
        assertThat(get("/api/v1/schools/" + ids[2] + "/manager-history", admin).body()).isEqualTo("[]");
    }

    @Test
    void removingAZoneThatStillHasTheManagersSchoolsIsRefusedNamingThem() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());

        Resp refused = assignZonesRaw(admin, manager.managerId()); // empty set drops the Zone

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("still has Schools in that Zone");
        assertThat(get("/api/v1/managers/" + manager.managerId(), admin).body()).contains(ids[0].toString());
    }

    @Test
    void movingASchoolToAZoneItsManagerDoesNotCoverIsRefusedAndAZoneWithManagersCannotBeDeleted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        UUID otherZone = zone(admin);
        UUID otherPlace = place(admin, otherZone);
        long version = ((Number) get("/api/v1/schools/" + ids[2], admin).map().get("version")).longValue();

        Resp move = put("/api/v1/schools/" + ids[2] + "/place", admin, Map.of("placeId", otherPlace, "version", version));
        assertThat(move.status()).isEqualTo(409);
        assertThat(move.body()).contains("must cover the School's Zone");

        // once the Manager also covers the other Zone the same move succeeds
        assignZones(admin, manager.managerId(), ids[0], otherZone);
        long fresh = ((Number) get("/api/v1/schools/" + ids[2], admin).map().get("version")).longValue();
        assertThat(put("/api/v1/schools/" + ids[2] + "/place", admin, Map.of("placeId", otherPlace, "version", fresh))
                        .status())
                .isEqualTo(200);

        UUID emptyZone = zone(admin);
        assignZones(admin, manager.managerId(), ids[0], otherZone, emptyZone);
        Resp deleteRefused = delete("/api/v1/zones/" + emptyZone, admin);
        assertThat(deleteRefused.status()).isEqualTo(409);
        assertThat(deleteRefused.body()).contains("Managers are still assigned");
    }

    @Test
    void reassigningASchoolKeepsHistoryAndUnassigningWorks() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx first = newManager(admin, ids[0]);
        ManagerCtx second = newManager(admin, ids[0]);

        assignSchoolManager(admin, ids[2], first.managerId());
        assignSchoolManager(admin, ids[2], second.managerId());
        assertThat(get("/api/v1/schools/" + ids[2], admin).body()).contains(second.managerId().toString());
        String history = get("/api/v1/schools/" + ids[2] + "/manager-history", admin).body();
        assertThat(history).contains(first.managerId().toString(), second.managerId().toString());
        assertThat(history).contains("endsOn\":\"");

        assertThat(assignSchoolManagerRaw(admin, ids[2], null).status()).isEqualTo(204);
        assertThat(get("/api/v1/schools/" + ids[2], admin).body()).contains("\"needsManager\":true");
        assertChangeRecorded(admin, "SCHOOL_MANAGER_ASSIGNMENT", ids[2], "manager");
    }

    @Test
    void assigningAnInactiveManagerIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        post("/api/v1/identity/users/" + manager.signed().userId() + "/deactivate", admin, null);
        // the account-sync listener runs after commit
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        assignSchoolManagerRaw(admin, ids[2], manager.managerId()).status())
                .isEqualTo(409));
    }

    @Test
    void concurrentZoneRemovalAndSchoolAssignmentNeverBreakTheInvariant() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        long version = ((Number) get("/api/v1/managers/" + manager.managerId(), admin).map().get("version")).longValue();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> remove = pool.submit(() -> {
            start.await();
            return put(
                            "/api/v1/managers/" + manager.managerId() + "/zones",
                            admin,
                            Map.of("zoneIds", List.of(), "version", version))
                    .status();
        });
        Future<Integer> assign = pool.submit(() -> {
            start.await();
            return assignSchoolManagerRaw(admin, ids[2], manager.managerId()).status();
        });
        start.countDown();
        int removeStatus = remove.get();
        int assignStatus = assign.get();
        pool.shutdown();

        // Either order is valid, but never both succeeding: that would leave a School whose Manager
        // no longer covers its Zone.
        assertThat(removeStatus == 200 && assignStatus == 204).isFalse();
        boolean covered = get("/api/v1/managers/" + manager.managerId(), admin).body().contains(ids[0].toString());
        boolean assigned = get("/api/v1/schools/" + ids[2], admin).body().contains(manager.managerId().toString());
        assertThat(assigned && !covered).isFalse();
    }
}
