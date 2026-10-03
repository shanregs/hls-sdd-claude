package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** User Story 6 (FR-007/FR-011): the last-admin safeguard end to end, through the real endpoints. */
class LastAdminSafeguardIntegrationTest extends UserManagementTestBase {

    @Test
    void soleAdminCannotBeDeactivatedOrStrippedOfAdminUntilASecondAdminExists() {
        deactivateEveryExistingAdmin();
        Signed first = signInAs(Role.ADMIN);
        String userPath = "/api/v1/identity/users/" + first.userId();

        assertThat(post(userPath + "/deactivate", first.token(), null).status()).isEqualTo(409);
        assertThat(put(userPath + "/roles", first.token(), Map.of("roles", Set.of("ADMIN", "MANAGER")))
                        .status())
                .isEqualTo(200); // adding a role is fine
        assertThat(put(userPath + "/roles", first.token(), Map.of("roles", Set.of("MANAGER")))
                        .status())
                .isEqualTo(409);
        assertThat(appUserRepository.findById(first.userId()).orElseThrow().isActive())
                .isTrue();
        assertThat(userAdminService.rolesOf(first.userId())).contains(Role.ADMIN);

        // a second Admin created through the real endpoint unlocks both actions on the first
        Map<String, Object> second = createUserViaApi(first.token(), "Second Admin", Set.of(Role.ADMIN));
        assertThat(second.get("active")).isEqualTo(true);
        assertThat(put(userPath + "/roles", first.token(), Map.of("roles", Set.of("MANAGER")))
                        .status())
                .isEqualTo(200);
        assertThat(userAdminService.rolesOf(first.userId())).containsExactly(Role.MANAGER);
    }

    @Test
    void twoAdminsDeactivatingEachOtherConcurrentlyNeverLeaveZeroActiveAdmins() throws Exception {
        deactivateEveryExistingAdmin();
        UUID a = userAdminService
                .createUser("Race A", nextPhone(), Set.of(Role.ADMIN), null, PASSWORD)
                .getId();
        UUID b = userAdminService
                .createUser("Race B", nextPhone(), Set.of(Role.ADMIN), null, PASSWORD)
                .getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID[] pair : new UUID[][] {{a, b}, {b, a}}) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    userAdminService.deactivateUser(pair[0], pair[1]);
                    return true;
                } catch (UserAdminService.LastAdminException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        long succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        long activeAdmins = Set.of(a, b).stream()
                .filter(id -> appUserRepository.findById(id).orElseThrow().isActive())
                .count();
        assertThat(activeAdmins).isEqualTo(1);
    }
}
