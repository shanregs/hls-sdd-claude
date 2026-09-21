package com.hls.organization;

import com.hls.organization.api.AssignmentConflictException;
import com.hls.organization.api.dto.*;
import com.hls.organization.internal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for FR-001/002/003/004/005/008/009/010/011/013 (User Stories
 * 1-3). Repositories are mocked; the real DB constraint (FR-003) and end-to-end
 * HTTP flow live in {@code OrganizationIntegrationTest}.
 */
class AccountabilityServiceTest {

    private SchoolAssignmentRepository schoolRepo;
    private TeacherAssignmentRepository teacherRepo;
    private Clock clock;
    private AccountabilityService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @BeforeEach
    void setUp() {
        schoolRepo = mock(SchoolAssignmentRepository.class);
        teacherRepo = mock(TeacherAssignmentRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountabilityService(schoolRepo, teacherRepo, clock);
    }

    // ---- User Story 1: assign -----------------------------------------------------------

    @Test
    void assignSchoolManager_whenUnassigned_createsFirstAssignment() {
        UUID schoolId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
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

    // ---- User Story 2: reassign, history, conflict, independence (FR-013) ---------------

    @Test
    void assignSchoolManager_reassign_endsPriorAndOpensNew() {
        UUID schoolId = UUID.randomUUID();
        UUID oldManager = UUID.randomUUID();
        UUID newManager = UUID.randomUUID();
        UUID currentAssignmentId = UUID.randomUUID();
        when(schoolRepo.endIfStillCurrent(currentAssignmentId, NOW)).thenReturn(1);

        CurrentAssignment result = service.assignSchoolManager(schoolId, newManager, currentAssignmentId, UUID.randomUUID());

        assertThat(result.managerId()).isEqualTo(newManager);
        verify(schoolRepo).endIfStillCurrent(currentAssignmentId, NOW);
        verify(schoolRepo).save(argThat(a -> a.getManagerId().equals(newManager) && a.isCurrent()));
    }

    @Test
    void assignSchoolManager_whenEndsAssignmentIdAlreadyEnded_throwsConflict() {
        UUID assignmentId = UUID.randomUUID();
        when(schoolRepo.endIfStillCurrent(assignmentId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.assignSchoolManager(UUID.randomUUID(), UUID.randomUUID(), assignmentId, UUID.randomUUID()))
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

        // Reassign an unrelated School's manager — the service call itself never
        // touches teacherRepo at all, which is the structural guarantee FR-013 requires.
        when(schoolRepo.endIfStillCurrent(any(), eq(NOW))).thenReturn(1);
        service.assignSchoolManager(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        verifyNoInteractions(teacherRepo);
        AccountabilityAnswer teacherAnswer = service.currentManagerForTeacher(teacherId);
        assertThat(teacherAnswer.managerId()).isEqualTo(teachersOwnManager);
    }

    // ---- User Story 3: portfolio & unassigned --------------------------------------------

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

    // Documents the accepted narrowing (research.md §3-equivalent for this module):
    // an identifier with zero rows at all is simply absent, not listed as "never assigned."
    @Test
    void unassigned_doesNotAndCannotListAnIdentifierWithNoRowsAtAll() {
        when(schoolRepo.findAll()).thenReturn(List.of());
        when(teacherRepo.findAll()).thenReturn(List.of());

        List<UnassignedItem> unassigned = service.unassigned(null);

        assertThat(unassigned).isEmpty();
    }
}
