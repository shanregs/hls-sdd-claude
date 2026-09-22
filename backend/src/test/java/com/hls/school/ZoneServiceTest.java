package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.school.api.ZoneAssignmentConflictException;
import com.hls.school.api.dto.SchoolZoneAnswer;
import com.hls.school.api.dto.ZoneView;
import com.hls.school.internal.SchoolZoneAssignment;
import com.hls.school.internal.SchoolZoneAssignmentRepository;
import com.hls.school.internal.Zone;
import com.hls.school.internal.ZoneRepository;
import com.hls.school.internal.ZoneService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for FR-001-007 (User Stories 1-3). Repositories are mocked;
 * the real DB constraint (FR-004) and end-to-end HTTP flow live in
 * {@code SchoolIntegrationTest}.
 */
class ZoneServiceTest {

    private ZoneRepository zoneRepository;
    private SchoolZoneAssignmentRepository assignmentRepository;
    private Clock clock;
    private ZoneService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ZoneRepository.class);
        assignmentRepository = mock(SchoolZoneAssignmentRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ZoneService(zoneRepository, assignmentRepository, clock);
    }

    // ---- User Story 1: createZone / findById ----------------------------------------------

    @Test
    void createZone_persistsAndReturnsNewZone() {
        UUID actingUserId = UUID.randomUUID();

        UUID zoneId = service.createZone("North Chennai", actingUserId);

        assertThat(zoneId).isNotNull();
        verify(zoneRepository).save(org.mockito.ArgumentMatchers.argThat(zone ->
                zone.getId().equals(zoneId) && zone.getName().equals("North Chennai")
                        && zone.getCreatedBy().equals(actingUserId) && zone.getCreatedAt().equals(NOW)));
    }

    @Test
    void findById_returnsTheZoneWithItsExactName() {
        UUID zoneId = UUID.randomUUID();
        Zone zone = new Zone(zoneId, "South Chennai", NOW, UUID.randomUUID());
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));

        Optional<ZoneView> result = service.findById(zoneId);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("South Chennai");
    }

    @Test
    void findById_forUnknownId_returnsEmpty() {
        UUID zoneId = UUID.randomUUID();
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.empty());

        assertThat(service.findById(zoneId)).isEmpty();
    }

    // ---- User Story 2: assignSchoolToZone --------------------------------------------------

    @Test
    void assignSchoolToZone_whenUnassigned_createsFirstAssignment() {
        UUID schoolId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();

        service.assignSchoolToZone(schoolId, zoneId, null, actingUserId);

        verify(assignmentRepository).save(org.mockito.ArgumentMatchers.argThat(a ->
                a.getSchoolId().equals(schoolId) && a.getZoneId().equals(zoneId) && a.isCurrent()));
    }

    @Test
    void assignSchoolToZone_withSameZoneAlreadyCurrent_isNoOp() {
        UUID schoolId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        SchoolZoneAssignment existing = new SchoolZoneAssignment(
                UUID.randomUUID(), schoolId, zoneId, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(Optional.of(existing));

        var result = service.assignSchoolToZone(schoolId, zoneId, null, UUID.randomUUID());

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignSchoolToZone_toADifferentZoneWithNoEndsAssignmentId_isRejectedAsConflict() {
        UUID schoolId = UUID.randomUUID();
        UUID currentZoneId = UUID.randomUUID();
        UUID differentZoneId = UUID.randomUUID();
        SchoolZoneAssignment existing = new SchoolZoneAssignment(
                UUID.randomUUID(), schoolId, currentZoneId, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.assignSchoolToZone(schoolId, differentZoneId, null, UUID.randomUUID()))
                .isInstanceOf(ZoneAssignmentConflictException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignSchoolToZone_withEndsAssignmentId_endsPriorRowAndOpensNewOne() {
        UUID schoolId = UUID.randomUUID();
        UUID newZoneId = UUID.randomUUID();
        UUID priorAssignmentId = UUID.randomUUID();
        when(assignmentRepository.endIfStillCurrent(priorAssignmentId, NOW)).thenReturn(1);

        service.assignSchoolToZone(schoolId, newZoneId, priorAssignmentId, UUID.randomUUID());

        verify(assignmentRepository).endIfStillCurrent(priorAssignmentId, NOW);
        verify(assignmentRepository).save(org.mockito.ArgumentMatchers.argThat(a ->
                a.getSchoolId().equals(schoolId) && a.getZoneId().equals(newZoneId) && a.isCurrent()));
    }

    @Test
    void assignSchoolToZone_namingAnAlreadyEndedAssignment_throwsConflict() {
        UUID priorAssignmentId = UUID.randomUUID();
        when(assignmentRepository.endIfStillCurrent(priorAssignmentId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.assignSchoolToZone(
                UUID.randomUUID(), UUID.randomUUID(), priorAssignmentId, UUID.randomUUID()))
                .isInstanceOf(ZoneAssignmentConflictException.class);
        verify(assignmentRepository, never()).save(any());
    }

    // ---- User Story 3: currentSchoolsForZone / currentZoneForSchool -----------------------

    @Test
    void currentSchoolsForZone_returnsExactlyTheSchoolsCurrentlyInThatZone() {
        UUID zoneId = UUID.randomUUID();
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        when(assignmentRepository.findByZoneIdAndEffectiveToIsNull(zoneId)).thenReturn(List.of(
                new SchoolZoneAssignment(UUID.randomUUID(), schoolA, zoneId, NOW, UUID.randomUUID(), NOW),
                new SchoolZoneAssignment(UUID.randomUUID(), schoolB, zoneId, NOW, UUID.randomUUID(), NOW)));

        List<UUID> result = service.currentSchoolsForZone(zoneId);

        assertThat(result).containsExactlyInAnyOrder(schoolA, schoolB);
    }

    @Test
    void currentZoneForSchool_whenNeverAssigned_returnsUnassignedNotError() {
        UUID schoolId = UUID.randomUUID();
        when(assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(Optional.empty());

        SchoolZoneAnswer answer = service.currentZoneForSchool(schoolId);

        assertThat(answer.state()).isEqualTo(SchoolZoneAnswer.State.UNASSIGNED);
    }

    @Test
    void currentZoneForSchool_whenAssigned_returnsCurrentZone() {
        UUID schoolId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        when(assignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(
                Optional.of(new SchoolZoneAssignment(UUID.randomUUID(), schoolId, zoneId, NOW, UUID.randomUUID(), NOW)));

        SchoolZoneAnswer answer = service.currentZoneForSchool(schoolId);

        assertThat(answer.state()).isEqualTo(SchoolZoneAnswer.State.CURRENT_ZONE);
        assertThat(answer.zoneId()).isEqualTo(zoneId);
    }
}
