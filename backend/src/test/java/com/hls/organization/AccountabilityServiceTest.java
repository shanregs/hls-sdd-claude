package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hls.organization.api.AssignmentConflictException;
import com.hls.organization.api.SchoolManagerNotInZoneException;
import com.hls.organization.api.ZoneNotFoundException;
import com.hls.organization.api.dto.*;
import com.hls.organization.internal.*;
import com.hls.school.api.ZoneQueries;
import com.hls.school.api.dto.SchoolZoneAnswer;
import com.hls.school.api.dto.ZoneView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for FR-001/002/003/004/005/008/009/010/011/013 (specs/003,
 * User Stories 1-3) and specs/006-zone-scoping's FR-001-008 (reworked).
 * Repositories and {@code school.api.ZoneQueries} are mocked; the real DB
 * constraint and end-to-end HTTP flow live in {@code OrganizationIntegrationTest}.
 */
class AccountabilityServiceTest {

    private SchoolAssignmentRepository schoolRepo;
    private TeacherAssignmentRepository teacherRepo;
    private ZoneManagerAssignmentRepository zoneManagerRepo;
    private ZoneQueries zoneQueries;
    private Clock clock;
    private AccountabilityService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @BeforeEach
    void setUp() {
        schoolRepo = mock(SchoolAssignmentRepository.class);
        teacherRepo = mock(TeacherAssignmentRepository.class);
        zoneManagerRepo = mock(ZoneManagerAssignmentRepository.class);
        zoneQueries = mock(ZoneQueries.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountabilityService(schoolRepo, teacherRepo, zoneManagerRepo, zoneQueries, clock);
    }

    /** Stubs a School as currently in {@code zoneId}, with {@code managerId} currently covering that Zone. */
    private void schoolInZoneCoveredBy(UUID schoolId, UUID zoneId, UUID managerId) {
        when(zoneQueries.currentZoneForSchool(schoolId)).thenReturn(SchoolZoneAnswer.currentZone(zoneId));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId))
                .thenReturn(Optional.of(new ZoneManagerAssignment(UUID.randomUUID(), zoneId, managerId, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600))));
    }

    // ---- specs/003 User Story 1: assign ---------------------------------------------------

    @Test
    void assignSchoolManager_whenUnassigned_createsFirstAssignment() {
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolId, zoneId, managerId);
        when(schoolRepo.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(Optional.empty());

        CurrentAssignment result = service.assignSchoolManager(schoolId, managerId, null, actingUserId);

        assertThat(result.managerId()).isEqualTo(managerId);
        verify(schoolRepo).save(argThat(a -> a.getSchoolId().equals(schoolId)
                && a.getManagerId().equals(managerId) && a.isCurrent()));
    }

    @Test
    void assignSchoolManager_withSameManagerAlreadyCurrent_isNoOp() {
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolId, zoneId, managerId);
        SchoolAssignment existing = new SchoolAssignment(UUID.randomUUID(), schoolId, managerId, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(schoolRepo.findBySchoolIdAndEffectiveToIsNull(schoolId)).thenReturn(Optional.of(existing));

        CurrentAssignment result = service.assignSchoolManager(schoolId, managerId, null, UUID.randomUUID());

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(schoolRepo, never()).save(any());
    }

    @Test
    void assignTeacherManager_whenUnassigned_createsFirstAssignment() {
        UUID teacherId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(teacherRepo.findByTeacherIdAndEffectiveToIsNull(teacherId)).thenReturn(Optional.empty());

        CurrentAssignment result = service.assignTeacherManager(teacherId, managerId, null, UUID.randomUUID());

        assertThat(result.managerId()).isEqualTo(managerId);
        verify(teacherRepo).save(any());
    }

    // ---- specs/003 User Story 2: reassign, history, conflict, independence (FR-013) -----

    @Test
    void assignSchoolManager_reassign_endsPriorAndOpensNew() {
        UUID schoolId = UUID.randomUUID();
        UUID newManager = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID currentAssignmentId = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolId, zoneId, newManager);
        when(schoolRepo.endIfStillCurrent(currentAssignmentId, NOW)).thenReturn(1);

        CurrentAssignment result = service.assignSchoolManager(schoolId, newManager, currentAssignmentId, UUID.randomUUID());

        assertThat(result.managerId()).isEqualTo(newManager);
        verify(schoolRepo).endIfStillCurrent(currentAssignmentId, NOW);
        verify(schoolRepo).save(argThat(a -> a.getManagerId().equals(newManager) && a.isCurrent()));
    }

    @Test
    void assignSchoolManager_whenEndsAssignmentIdAlreadyEnded_throwsConflict() {
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolId, zoneId, managerId);
        when(schoolRepo.endIfStillCurrent(assignmentId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.assignSchoolManager(schoolId, managerId, assignmentId, UUID.randomUUID()))
                .isInstanceOf(AssignmentConflictException.class);

        verify(schoolRepo, never()).save(any());
    }

    @Test
    void endSchoolAssignment_whenAlreadyEnded_throwsConflict() {
        UUID assignmentId = UUID.randomUUID();
        when(schoolRepo.endIfStillCurrent(assignmentId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.endSchoolAssignment(assignmentId, UUID.randomUUID()))
                .isInstanceOf(AssignmentConflictException.class);
    }

    @Test
    void schoolAssignmentHistory_returnsAllPeriodsOldestFirst() {
        UUID schoolId = UUID.randomUUID();
        SchoolAssignment first = new SchoolAssignment(UUID.randomUUID(), schoolId, UUID.randomUUID(), NOW.minusSeconds(7200), UUID.randomUUID(), NOW.minusSeconds(7200));
        first.end(NOW.minusSeconds(3600));
        SchoolAssignment second = new SchoolAssignment(UUID.randomUUID(), schoolId, UUID.randomUUID(), NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(schoolRepo.findBySchoolIdOrderByEffectiveFromAsc(schoolId)).thenReturn(List.of(first, second));

        List<AssignmentHistoryEntry> history = service.schoolAssignmentHistory(schoolId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).effectiveTo()).isEqualTo(second.getEffectiveFrom());
        assertThat(history.get(1).effectiveTo()).isNull();
    }

    @Test
    void managerForSchoolAsOf_pastDate_returnsThePeriodManager() {
        UUID schoolId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        Instant reassignedAt = NOW.minusSeconds(1800);
        SchoolAssignment periodA = new SchoolAssignment(UUID.randomUUID(), schoolId, managerA, NOW.minusSeconds(7200), UUID.randomUUID(), NOW.minusSeconds(7200));
        periodA.end(reassignedAt);
        SchoolAssignment periodB = new SchoolAssignment(UUID.randomUUID(), schoolId, managerB, reassignedAt, UUID.randomUUID(), reassignedAt);
        when(schoolRepo.findBySchoolIdOrderByEffectiveFromAsc(schoolId)).thenReturn(List.of(periodA, periodB));

        AccountabilityAnswer beforeReassignment = service.managerForSchoolAsOf(schoolId, NOW.minusSeconds(3600));

        assertThat(beforeReassignment.state()).isEqualTo(AccountabilityAnswer.State.CURRENT_MANAGER);
        assertThat(beforeReassignment.managerId()).isEqualTo(managerA);
    }

    // FR-013: reassigning a School's Manager must not touch an independently-assigned Teacher's Manager.
    @Test
    void reassigningSchoolManager_leavesIndependentlyAssignedTeacherManagerUnchanged() {
        UUID teacherId = UUID.randomUUID();
        UUID teachersOwnManager = UUID.randomUUID();
        when(teacherRepo.findByTeacherIdAndEffectiveToIsNull(teacherId))
                .thenReturn(Optional.of(new TeacherAssignment(UUID.randomUUID(), teacherId, teachersOwnManager, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600))));

        UUID schoolId = UUID.randomUUID();
        UUID schoolManagerId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolId, zoneId, schoolManagerId);
        // Reassign an unrelated School's manager — the service call itself never
        // touches teacherRepo at all, which is the structural guarantee FR-013 requires.
        when(schoolRepo.endIfStillCurrent(any(), eq(NOW))).thenReturn(1);
        service.assignSchoolManager(schoolId, schoolManagerId, UUID.randomUUID(), UUID.randomUUID());

        verifyNoInteractions(teacherRepo);
        AccountabilityAnswer teacherAnswer = service.currentManagerForTeacher(teacherId);
        assertThat(teacherAnswer.managerId()).isEqualTo(teachersOwnManager);
    }

    // ---- specs/003 User Story 3: portfolio & unassigned ----------------------------------

    @Test
    void portfolioForManager_returnsOnlyCurrentSchoolsAndTeachers() {
        UUID managerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        when(schoolRepo.findByManagerIdAndEffectiveToIsNull(managerId)).thenReturn(
                List.of(new SchoolAssignment(UUID.randomUUID(), schoolId, managerId, NOW, UUID.randomUUID(), NOW)));
        when(teacherRepo.findByManagerIdAndEffectiveToIsNull(managerId)).thenReturn(
                List.of(new TeacherAssignment(UUID.randomUUID(), teacherId, managerId, NOW, UUID.randomUUID(), NOW)));

        List<PortfolioItem> portfolio = service.portfolioForManager(managerId);

        assertThat(portfolio).hasSize(2);
        assertThat(portfolio).extracting(PortfolioItem::itemType).containsExactlyInAnyOrder(ItemType.SCHOOL, ItemType.TEACHER);
    }

    @Test
    void unassigned_includesSchoolEndedWithoutReplacement_withLastEndedAt() {
        UUID schoolId = UUID.randomUUID();
        SchoolAssignment ended = new SchoolAssignment(UUID.randomUUID(), schoolId, UUID.randomUUID(), NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        ended.end(NOW);
        when(schoolRepo.findAll()).thenReturn(List.of(ended));
        when(teacherRepo.findAll()).thenReturn(List.of());

        List<UnassignedItem> unassigned = service.unassigned(ItemType.SCHOOL);

        assertThat(unassigned).hasSize(1);
        assertThat(unassigned.get(0).itemId()).isEqualTo(schoolId);
        assertThat(unassigned.get(0).lastEndedAt()).isEqualTo(NOW);
    }

    // Documents the accepted narrowing: an identifier with zero rows at all is
    // simply absent, not listed as "never assigned."
    @Test
    void unassigned_doesNotAndCannotListAnIdentifierWithNoRowsAtAll() {
        when(schoolRepo.findAll()).thenReturn(List.of());
        when(teacherRepo.findAll()).thenReturn(List.of());

        List<UnassignedItem> unassigned = service.unassigned(null);

        assertThat(unassigned).isEmpty();
    }

    // ---- specs/006 User Story 1: assignManagerToZone / removeManagerFromZone ------------

    @Test
    void assignManagerToZone_newManager_createsAssignment() {
        UUID zoneId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(zoneQueries.findById(zoneId)).thenReturn(Optional.of(new ZoneView(zoneId, "North Chennai")));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId)).thenReturn(Optional.empty());

        ZoneManagerAssignmentView result = service.assignManagerToZone(zoneId, managerId, UUID.randomUUID());

        assertThat(result.zoneId()).isEqualTo(zoneId);
        assertThat(result.managerId()).isEqualTo(managerId);
        verify(zoneManagerRepo).save(argThat(a -> a.getZoneId().equals(zoneId) && a.getManagerId().equals(managerId) && a.isCurrent()));
    }

    @Test
    void assignManagerToZone_sameManagerAgain_isNoOp() {
        UUID zoneId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(zoneQueries.findById(zoneId)).thenReturn(Optional.of(new ZoneView(zoneId, "North Chennai")));
        ZoneManagerAssignment existing = new ZoneManagerAssignment(UUID.randomUUID(), zoneId, managerId, NOW.minusSeconds(3600), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId)).thenReturn(Optional.of(existing));

        ZoneManagerAssignmentView result = service.assignManagerToZone(zoneId, managerId, UUID.randomUUID());

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(zoneManagerRepo, never()).save(any());
    }

    @Test
    void assignManagerToZone_unknownZoneId_throwsZoneNotFound() {
        UUID zoneId = UUID.randomUUID();
        when(zoneQueries.findById(zoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignManagerToZone(zoneId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ZoneNotFoundException.class);
        verify(zoneManagerRepo, never()).save(any());
    }

    @Test
    void assignManagerToZone_secondDifferentManager_bothCurrentlyCoverTheSameZone() {
        UUID zoneId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        when(zoneQueries.findById(zoneId)).thenReturn(Optional.of(new ZoneView(zoneId, "North Chennai")));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(eq(zoneId), any())).thenReturn(Optional.empty());

        service.assignManagerToZone(zoneId, managerA, UUID.randomUUID());
        service.assignManagerToZone(zoneId, managerB, UUID.randomUUID());

        verify(zoneManagerRepo).save(argThat(a -> a.getManagerId().equals(managerA)));
        verify(zoneManagerRepo).save(argThat(a -> a.getManagerId().equals(managerB)));
    }

    @Test
    void removeManagerFromZone_endsAssignment() {
        UUID assignmentId = UUID.randomUUID();
        when(zoneManagerRepo.endIfStillCurrent(assignmentId, NOW)).thenReturn(1);

        service.removeManagerFromZone(assignmentId, UUID.randomUUID());

        verify(zoneManagerRepo).endIfStillCurrent(assignmentId, NOW);
    }

    @Test
    void removeManagerFromZone_alreadyEnded_throwsConflict() {
        UUID assignmentId = UUID.randomUUID();
        when(zoneManagerRepo.endIfStillCurrent(assignmentId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.removeManagerFromZone(assignmentId, UUID.randomUUID()))
                .isInstanceOf(AssignmentConflictException.class);
    }

    // ---- specs/006 User Story 2: assignSchoolManager is Zone-constrained ----------------

    @Test
    void assignSchoolManager_managerNotCoveringZone_throwsSchoolManagerNotInZone() {
        UUID schoolId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(zoneQueries.currentZoneForSchool(schoolId)).thenReturn(SchoolZoneAnswer.currentZone(zoneId));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignSchoolManager(schoolId, managerId, null, UUID.randomUUID()))
                .isInstanceOf(SchoolManagerNotInZoneException.class);
        verify(schoolRepo, never()).save(any());
    }

    @Test
    void assignSchoolManager_schoolHasNoCurrentZone_throwsSchoolManagerNotInZone() {
        UUID schoolId = UUID.randomUUID();
        when(zoneQueries.currentZoneForSchool(schoolId)).thenReturn(SchoolZoneAnswer.unassigned());

        assertThatThrownBy(() -> service.assignSchoolManager(schoolId, UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(SchoolManagerNotInZoneException.class);
        verify(schoolRepo, never()).save(any());
    }

    @Test
    void assignSchoolManager_zoneHasNoCoveringManagers_throwsSchoolManagerNotInZone() {
        UUID schoolId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(zoneQueries.currentZoneForSchool(schoolId)).thenReturn(SchoolZoneAnswer.currentZone(zoneId));
        when(zoneManagerRepo.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignSchoolManager(schoolId, managerId, null, UUID.randomUUID()))
                .isInstanceOf(SchoolManagerNotInZoneException.class);
    }

    @Test
    void assignSchoolManager_twoSchoolsSameZoneDifferentManagers_bothSucceed() {
        UUID zoneId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        schoolInZoneCoveredBy(schoolA, zoneId, managerA);
        schoolInZoneCoveredBy(schoolB, zoneId, managerB);
        when(schoolRepo.findBySchoolIdAndEffectiveToIsNull(any())).thenReturn(Optional.empty());

        CurrentAssignment resultA = service.assignSchoolManager(schoolA, managerA, null, UUID.randomUUID());
        CurrentAssignment resultB = service.assignSchoolManager(schoolB, managerB, null, UUID.randomUUID());

        assertThat(resultA.managerId()).isEqualTo(managerA);
        assertThat(resultB.managerId()).isEqualTo(managerB);
    }

    @Test
    void assignTeacherManager_neverCallsZoneQueries() {
        service.assignTeacherManager(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID());

        verifyNoInteractions(zoneQueries);
    }

    // ---- specs/006 User Story 3: zoneCoverage --------------------------------------------

    @Test
    void zoneCoverage_returnsCurrentManagersAndSchools() {
        UUID zoneId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(zoneManagerRepo.findByZoneIdAndEffectiveToIsNull(zoneId)).thenReturn(List.of(
                new ZoneManagerAssignment(UUID.randomUUID(), zoneId, managerA, NOW, UUID.randomUUID(), NOW),
                new ZoneManagerAssignment(UUID.randomUUID(), zoneId, managerB, NOW, UUID.randomUUID(), NOW)));
        when(zoneQueries.currentSchoolsForZone(zoneId)).thenReturn(List.of(schoolId));

        ZoneCoverage coverage = service.zoneCoverage(zoneId);

        assertThat(coverage.managerIds()).containsExactlyInAnyOrder(managerA, managerB);
        assertThat(coverage.schoolIds()).containsExactly(schoolId);
    }

    @Test
    void zoneCoverage_zoneWithNoSchoolsYet_returnsEmptySchoolListNotError() {
        UUID zoneId = UUID.randomUUID();
        when(zoneManagerRepo.findByZoneIdAndEffectiveToIsNull(zoneId)).thenReturn(List.of());
        when(zoneQueries.currentSchoolsForZone(zoneId)).thenReturn(List.of());

        ZoneCoverage coverage = service.zoneCoverage(zoneId);

        assertThat(coverage.managerIds()).isEmpty();
        assertThat(coverage.schoolIds()).isEmpty();
    }
}
