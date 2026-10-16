package com.hls.identity.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.identity.user.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** {@code isGranted} is the non-throwing form of {@code require} (spec 038 FR-020). */
class PermissionGuardTest {

    private final PermissionMatrixService matrix = mock(PermissionMatrixService.class);
    private final PermissionGuard guard = new PermissionGuard(matrix);

    @Test
    void isGrantedIsTrueWhenAnyRoleHoldsThePermission() {
        when(matrix.isGranted(Role.ADMIN, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT)).thenReturn(true);

        assertThat(guard.isGranted(Set.of(Role.ADMIN), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT))
                .isTrue();
        assertThat(guard.isGranted(
                        Set.of(Role.TEACHER, Role.ADMIN), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT))
                .isTrue();
    }

    @Test
    void isGrantedIsFalseWhenNoRoleHoldsItOrThereAreNoRoles() {
        when(matrix.isGranted(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.DELETE))
                .thenReturn(false);

        assertThat(guard.isGranted(Set.of(Role.DIRECTOR), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.DELETE))
                .isFalse();
        assertThat(guard.isGranted(Set.of(), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW)).isFalse();
    }

    @Test
    void requireStillThrowsWhenNotGrantedAndPassesWhenGranted() {
        when(matrix.isGranted(Role.ADMIN, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW)).thenReturn(true);

        guard.require(Set.of(Role.ADMIN), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW);
        assertThatThrownBy(() ->
                        guard.require(Set.of(Role.DIRECTOR), PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW))
                .isInstanceOf(AccessDeniedException.class);
    }
}
