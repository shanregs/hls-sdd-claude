package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Spec 004 FR-007: the guard's decision table (single Admin, two Admins, deactivated Admin). */
class LastAdminGuardTest {

    private final RoleAssignmentRepository roleAssignments = mock(RoleAssignmentRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final LastAdminGuard guard = new LastAdminGuard(roleAssignments, users);

    private AppUser admin(UUID id, boolean active) {
        AppUser user = new AppUser("Admin", "98" + id.toString().substring(0, 8).replaceAll("\\D", "1"), null, null);
        ReflectionTestUtils.setField(user, "id", id);
        if (!active) {
            user.deactivate();
        }
        when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        return user;
    }

    private void givenAdmins(AppUser... admins) {
        when(roleAssignments.findByRole(Role.ADMIN))
                .thenReturn(java.util.Arrays.stream(admins)
                        .map(a -> new RoleAssignment(a.getId(), Role.ADMIN))
                        .toList());
        when(users.findAllById(anyIterable())).thenReturn(List.of(admins));
    }

    @Test
    void soleActiveAdminCannotBeDeactivatedOrLoseAdmin() {
        AppUser sole = admin(UUID.randomUUID(), true);
        givenAdmins(sole);

        assertThat(guard.wouldLeaveNoActiveAdmin(sole.getId(), Set.of(), true)).isTrue();
        assertThat(guard.wouldLeaveNoActiveAdmin(sole.getId(), Set.of(Role.ADMIN), false)).isTrue();
        assertThat(guard.wouldLeaveNoActiveAdmin(sole.getId(), Set.of(Role.ADMIN, Role.MANAGER), false))
                .isTrue();
    }

    @Test
    void removingAnotherRoleFromTheSoleAdminIsAllowed() {
        AppUser sole = admin(UUID.randomUUID(), true);
        givenAdmins(sole);

        assertThat(guard.wouldLeaveNoActiveAdmin(sole.getId(), Set.of(Role.MANAGER), false))
                .isFalse();
    }

    @Test
    void withTwoActiveAdminsEitherActionIsAllowed() {
        AppUser a = admin(UUID.randomUUID(), true);
        AppUser b = admin(UUID.randomUUID(), true);
        givenAdmins(a, b);

        assertThat(guard.wouldLeaveNoActiveAdmin(a.getId(), Set.of(), true)).isFalse();
        assertThat(guard.wouldLeaveNoActiveAdmin(a.getId(), Set.of(Role.ADMIN), false))
                .isFalse();
    }

    @Test
    void aDeactivatedAdminDoesNotCountAsKeepingTheSystemUnlocked() {
        AppUser active = admin(UUID.randomUUID(), true);
        AppUser inactive = admin(UUID.randomUUID(), false);
        givenAdmins(active, inactive);

        assertThat(guard.wouldLeaveNoActiveAdmin(active.getId(), Set.of(), true)).isTrue();
    }

    @Test
    void changingAnInactiveAdminIsAlwaysAllowed() {
        AppUser active = admin(UUID.randomUUID(), true);
        AppUser inactive = admin(UUID.randomUUID(), false);
        givenAdmins(active, inactive);

        assertThat(guard.wouldLeaveNoActiveAdmin(inactive.getId(), Set.of(Role.ADMIN), false))
                .isFalse();
    }
}
