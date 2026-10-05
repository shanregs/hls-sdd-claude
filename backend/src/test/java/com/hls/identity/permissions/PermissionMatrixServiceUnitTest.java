package com.hls.identity.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.cache.SnapshotCaches;
import com.hls.identity.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit test for {@link PermissionMatrixService}'s edit/safeguard logic, independent of the DB/HTTP
 * layer (FR-003/FR-004) — {@link PermissionMatrixServiceTest} covers the same rules end-to-end
 * through the real HTTP endpoints.
 */
class PermissionMatrixServiceUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void validEditPublishesAChangeRecordWithBeforeAndAfter() {
        PermissionMatrixRepository repository = mock(PermissionMatrixRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.findByRoleAndModuleAndAction(Role.MANAGER, PermissionModule.DASHBOARD, PermissionAction.VIEW))
                .thenReturn(Optional.of(
                        new PermissionMatrixEntry(Role.MANAGER, PermissionModule.DASHBOARD, PermissionAction.VIEW, true, CLOCK.instant())));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionMatrixService service = new PermissionMatrixService(repository, eventPublisher, CLOCK, new SnapshotCaches(CLOCK, false, 60));
        PermissionMatrixService.UpdateResult result =
                service.updateGrant(Role.MANAGER, PermissionModule.DASHBOARD, PermissionAction.VIEW, false, ACTOR_ID);

        assertThat(result.success()).isTrue();
        assertThat(result.entry().isGranted()).isFalse();

        ArgumentCaptor<PermissionMatrixChanged> captor = ArgumentCaptor.forClass(PermissionMatrixChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PermissionMatrixChanged event = captor.getValue();
        assertThat(event.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.role()).isEqualTo(Role.MANAGER);
        assertThat(event.module()).isEqualTo(PermissionModule.DASHBOARD);
        assertThat(event.action()).isEqualTo(PermissionAction.VIEW);
        assertThat(event.before()).isTrue();
        assertThat(event.after()).isFalse();
    }

    @Test
    void safeguardRejectsRemovingTheLastMatrixManagerWithoutPublishingAnEvent() {
        PermissionMatrixRepository repository = mock(PermissionMatrixRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        // ADMIN currently holds the manage-matrix grant; DIRECTOR and SYSTEM do not.
        when(repository.findByRoleAndModuleAndAction(
                        Role.ADMIN, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.ADMIN, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, true, CLOCK.instant())));
        when(repository.findByRoleAndModuleAndAction(
                        Role.DIRECTOR, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.DIRECTOR, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, false, CLOCK.instant())));
        when(repository.findByRoleAndModuleAndAction(
                        Role.SYSTEM, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.SYSTEM, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, false, CLOCK.instant())));

        when(repository.findAll()).thenReturn(List.of(
                manageGrant(Role.ADMIN, true), manageGrant(Role.DIRECTOR, false), manageGrant(Role.SYSTEM, false)));

        PermissionMatrixService service = new PermissionMatrixService(repository, eventPublisher, CLOCK, new SnapshotCaches(CLOCK, false, 60));
        PermissionMatrixService.UpdateResult result = service.updateGrant(
                Role.ADMIN, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, false, ACTOR_ID);

        assertThat(result.success()).isFalse();
        assertThat(result.rejectionReason()).isNotBlank();
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void revokingOneManagerRoleStillSucceedsWhenAnotherRemains() {
        PermissionMatrixRepository repository = mock(PermissionMatrixRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.findByRoleAndModuleAndAction(
                        Role.DIRECTOR, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.DIRECTOR, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, true, CLOCK.instant())));
        when(repository.findByRoleAndModuleAndAction(
                        Role.ADMIN, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.ADMIN, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, true, CLOCK.instant())));
        when(repository.findByRoleAndModuleAndAction(
                        Role.SYSTEM, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT))
                .thenReturn(Optional.of(new PermissionMatrixEntry(
                        Role.SYSTEM, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, false, CLOCK.instant())));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(List.of(
                manageGrant(Role.DIRECTOR, true), manageGrant(Role.ADMIN, true), manageGrant(Role.SYSTEM, false)));

        PermissionMatrixService service = new PermissionMatrixService(repository, eventPublisher, CLOCK, new SnapshotCaches(CLOCK, false, 60));
        PermissionMatrixService.UpdateResult result = service.updateGrant(
                Role.DIRECTOR, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, false, ACTOR_ID);

        assertThat(result.success()).isTrue();
    }

    /** The manage-the-matrix grant of one role, as the service reads it from the whole matrix. */
    private static PermissionMatrixEntry manageGrant(Role role, boolean granted) {
        return new PermissionMatrixEntry(
                role, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, granted, CLOCK.instant());
    }
}
