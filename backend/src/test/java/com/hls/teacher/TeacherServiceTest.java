package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditAction;
import com.hls.audit.api.dto.AuditRecordRequest;
import com.hls.teacher.api.dto.CreateTeacherProfileRequest;
import com.hls.teacher.api.dto.SalaryAsOfAnswer;
import com.hls.teacher.api.dto.TeacherProfileView;
import com.hls.teacher.api.dto.TeacherSalaryHistoryView;
import com.hls.teacher.api.dto.TeacherStatus;
import com.hls.teacher.api.dto.UpdateTeacherProfileRequest;
import com.hls.teacher.internal.TeacherProfile;
import com.hls.teacher.internal.TeacherProfileRepository;
import com.hls.teacher.internal.TeacherSalaryHistory;
import com.hls.teacher.internal.TeacherSalaryHistoryRepository;
import com.hls.teacher.internal.TeacherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for specs/005-teacher (FR-001-005/011) and
 * specs/009-teacher-salary-history (FR-001-009). Repositories and
 * {@link AuditWriter} are mocked; the real HTTP round trip and real Audit
 * history read-back live in {@code TeacherIntegrationTest}.
 */
class TeacherServiceTest {

    private TeacherProfileRepository teacherProfileRepository;
    private TeacherSalaryHistoryRepository teacherSalaryHistoryRepository;
    private AuditWriter auditWriter;
    private Clock clock;
    private TeacherService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @BeforeEach
    void setUp() {
        teacherProfileRepository = mock(TeacherProfileRepository.class);
        teacherSalaryHistoryRepository = mock(TeacherSalaryHistoryRepository.class);
        auditWriter = mock(AuditWriter.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TeacherService(teacherProfileRepository, teacherSalaryHistoryRepository, auditWriter, clock);
    }

    private TeacherProfile aProfile(UUID id, String name, TeacherStatus status) {
        return new TeacherProfile(id, name, "+919800000000", null, status, NOW.minusSeconds(3600), UUID.randomUUID());
    }

    private TeacherSalaryHistory anEntry(UUID teacherId, String amount, LocalDate effectiveFrom, Instant createdAt) {
        return new TeacherSalaryHistory(UUID.randomUUID(), teacherId, new BigDecimal(amount), effectiveFrom, createdAt, UUID.randomUUID());
    }

    // ---- specs/005 User Story 1: create / findById -----------------------------------------

    @Test
    void create_persistsAndReturnsProfileWithExactDetails() {
        UUID actingUserId = UUID.randomUUID();
        var request = new CreateTeacherProfileRequest("Priya Sharma", "+919811111111", "priya@example.com",
                new BigDecimal("17000.00"), TeacherStatus.IN_TRAINING);

        TeacherProfileView result = service.create(request, actingUserId);

        assertThat(result.name()).isEqualTo("Priya Sharma");
        assertThat(result.phone()).isEqualTo("+919811111111");
        assertThat(result.hlsOfferedSalary()).isEqualByComparingTo("17000.00");
        assertThat(result.status()).isEqualTo(TeacherStatus.IN_TRAINING);
        verify(teacherProfileRepository).save(argThat(p ->
                p.getName().equals("Priya Sharma") && p.getCreatedBy().equals(actingUserId) && p.getCreatedAt().equals(NOW)));
    }

    @Test
    void create_alsoRecordsFirstSalaryHistoryEntry_effectiveFromCreationDate() {
        UUID actingUserId = UUID.randomUUID();
        var request = new CreateTeacherProfileRequest("Priya Sharma", "+919811111111", null,
                new BigDecimal("17000.00"), TeacherStatus.IN_TRAINING);

        service.create(request, actingUserId);

        verify(teacherSalaryHistoryRepository).save(argThat(entry ->
                entry.getAmount().compareTo(new BigDecimal("17000.00")) == 0
                        && entry.getEffectiveFrom().equals(TODAY)
                        && entry.getCreatedBy().equals(actingUserId)));
    }

    @Test
    void create_recordsAuditCreatedEntry() {
        UUID actingUserId = UUID.randomUUID();
        var request = new CreateTeacherProfileRequest("Priya Sharma", "+919811111111", null,
                new BigDecimal("17000.00"), TeacherStatus.IN_TRAINING);

        service.create(request, actingUserId);

        verify(auditWriter).record(argThat((AuditRecordRequest r) ->
                r.sourceModule().equals("teacher") && r.entityType().equals("TeacherProfile")
                        && r.action() == AuditAction.CREATED && r.actorUserId().equals(actingUserId)));
    }

    @Test
    void findById_forUnknownId_returnsEmpty() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.empty());

        assertThat(service.findById(teacherId)).isEmpty();
    }

    // ---- specs/005 User Story 2: updateProfile / changeStatus (salary no longer here) ------

    @Test
    void updateProfile_changesOnlyTheProvidedFields_leavesOthersUntouched() {
        UUID teacherId = UUID.randomUUID();
        TeacherProfile existing = new TeacherProfile(teacherId, "Original Name", "+919800000001", "orig@example.com",
                TeacherStatus.IN_TRAINING, NOW.minusSeconds(3600), UUID.randomUUID());
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(existing));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of(anEntry(teacherId, "15000.00", TODAY.minusDays(30), NOW.minusSeconds(3600))));

        TeacherProfileView result = service.updateProfile(teacherId,
                new UpdateTeacherProfileRequest(null, "+919800000099", null), UUID.randomUUID());

        assertThat(result.name()).isEqualTo("Original Name");
        assertThat(result.phone()).isEqualTo("+919800000099");
        assertThat(result.hlsOfferedSalary()).isEqualByComparingTo("15000.00");
    }

    @Test
    void updateProfile_toTheExactCurrentValue_isUnaffectedEitherWay() {
        UUID teacherId = UUID.randomUUID();
        TeacherProfile existing = new TeacherProfile(teacherId, "Same Name", "+919800000002", null,
                TeacherStatus.ACTIVE, NOW.minusSeconds(3600), UUID.randomUUID());
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(existing));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of());

        TeacherProfileView result = service.updateProfile(teacherId,
                new UpdateTeacherProfileRequest("Same Name", null, null), UUID.randomUUID());

        assertThat(result.name()).isEqualTo("Same Name");
    }

    @Test
    void changeStatus_recordsBeforeAndAfterStatus() {
        UUID teacherId = UUID.randomUUID();
        TeacherProfile existing = new TeacherProfile(teacherId, "A Teacher", "+919800000003", null,
                TeacherStatus.IN_TRAINING, NOW.minusSeconds(3600), UUID.randomUUID());
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(existing));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of());

        TeacherProfileView result = service.changeStatus(teacherId, TeacherStatus.ACTIVE, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(TeacherStatus.ACTIVE);
        verify(auditWriter).record(argThat((AuditRecordRequest r) ->
                r.action() == AuditAction.UPDATED && r.beforeValue().contains("IN_TRAINING") && r.afterValue().contains("ACTIVE")));
    }

    @Test
    void changeStatus_reactivationAfterExited_isAllowed() {
        UUID teacherId = UUID.randomUUID();
        TeacherProfile existing = new TeacherProfile(teacherId, "A Teacher", "+919800000004", null,
                TeacherStatus.EXITED, NOW.minusSeconds(3600), UUID.randomUUID());
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(existing));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of());

        TeacherProfileView result = service.changeStatus(teacherId, TeacherStatus.IN_TRAINING, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(TeacherStatus.IN_TRAINING);
    }

    // ---- specs/005 User Story 3: exists -----------------------------------------------------

    @Test
    void exists_forKnownId_returnsTrue() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));

        assertThat(service.exists(teacherId)).isTrue();
    }

    @Test
    void exists_forUnknownId_returnsFalse() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.empty());

        assertThat(service.exists(teacherId)).isFalse();
    }

    // ---- specs/009 User Story 2: recordSalaryChange ------------------------------------------

    @Test
    void recordSalaryChange_becomesTheCurrentSalary() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of(anEntry(teacherId, "19000.00", TODAY, NOW)));

        TeacherSalaryHistoryView result = service.recordSalaryChange(teacherId, new BigDecimal("19000.00"), TODAY, UUID.randomUUID());

        assertThat(result.amount()).isEqualByComparingTo("19000.00");
        assertThat(result.effectiveFrom()).isEqualTo(TODAY);
        verify(teacherSalaryHistoryRepository).save(argThat(entry ->
                entry.getAmount().compareTo(new BigDecimal("19000.00")) == 0 && entry.getEffectiveFrom().equals(TODAY)));
    }

    @Test
    void recordSalaryChange_priorAmountStaysRetrievableAsOfItsOwnDate() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        LocalDate originalDate = TODAY.minusDays(60);
        LocalDate incrementDate = TODAY.minusDays(10);
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of(
                anEntry(teacherId, "19000.00", incrementDate, NOW.minusSeconds(600)),
                anEntry(teacherId, "17000.00", originalDate, NOW.minusSeconds(6000))));

        SalaryAsOfAnswer beforeIncrement = service.salaryAsOf(teacherId, incrementDate.minusDays(1));
        SalaryAsOfAnswer atOrAfterIncrement = service.salaryAsOf(teacherId, incrementDate);

        assertThat(beforeIncrement.amount()).isEqualByComparingTo("17000.00");
        assertThat(atOrAfterIncrement.amount()).isEqualByComparingTo("19000.00");
    }

    @Test
    void recordSalaryChange_withNoEffectiveFrom_defaultsToToday() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of());

        TeacherSalaryHistoryView result = service.recordSalaryChange(teacherId, new BigDecimal("20000.00"), null, UUID.randomUUID());

        assertThat(result.effectiveFrom()).isEqualTo(TODAY);
    }

    @Test
    void recordSalaryChange_backdatedCorrection_sortsIntoItsCorrectPlace() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        LocalDate backdated = TODAY.minusDays(90);
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of(
                anEntry(teacherId, "17000.00", TODAY.minusDays(30), NOW.minusSeconds(600)),
                anEntry(teacherId, "16000.00", backdated, NOW)));

        SalaryAsOfAnswer atBackdatedDate = service.salaryAsOf(teacherId, backdated);

        assertThat(atBackdatedDate.amount()).isEqualByComparingTo("16000.00");
    }

    @Test
    void recordSalaryChange_sameDayDuplicate_mostRecentlyRecordedWins() {
        UUID teacherId = UUID.randomUUID();
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of(
                anEntry(teacherId, "21000.00", TODAY, NOW),
                anEntry(teacherId, "20000.00", TODAY, NOW.minusSeconds(60))));

        SalaryAsOfAnswer answer = service.currentSalary(teacherId);

        assertThat(answer.amount()).isEqualByComparingTo("21000.00");
    }

    @Test
    void recordSalaryChange_recordsAuditEntry() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of());

        service.recordSalaryChange(teacherId, new BigDecimal("22000.00"), TODAY, UUID.randomUUID());

        verify(auditWriter).record(argThat((AuditRecordRequest r) ->
                r.action() == AuditAction.UPDATED && r.afterValue().contains("22000.00")));
    }

    // ---- specs/009 User Story 3: findById composes the current salary in -------------------

    @Test
    void findById_showsCurrentSalary_afterAnIncrement() {
        UUID teacherId = UUID.randomUUID();
        when(teacherProfileRepository.findById(teacherId)).thenReturn(Optional.of(aProfile(teacherId, "A Teacher", TeacherStatus.ACTIVE)));
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId)).thenReturn(List.of(
                anEntry(teacherId, "19000.00", TODAY.minusDays(1), NOW),
                anEntry(teacherId, "17000.00", TODAY.minusDays(30), NOW.minusSeconds(6000))));

        Optional<TeacherProfileView> result = service.findById(teacherId);

        assertThat(result).isPresent();
        assertThat(result.get().hlsOfferedSalary()).isEqualByComparingTo("19000.00");
    }

    // ---- specs/009 User Story 4: salaryAsOf edge cases --------------------------------------

    @Test
    void salaryAsOf_forADateBeforeTheFirstEntry_returnsNotYetRecorded() {
        UUID teacherId = UUID.randomUUID();
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of(anEntry(teacherId, "17000.00", TODAY.minusDays(10), NOW)));

        SalaryAsOfAnswer answer = service.salaryAsOf(teacherId, TODAY.minusDays(20));

        assertThat(answer.state()).isEqualTo(SalaryAsOfAnswer.State.NOT_YET_RECORDED);
        assertThat(answer.amount()).isNull();
    }

    @Test
    void salaryAsOf_forAFutureDate_returnsTheCurrentSalary() {
        UUID teacherId = UUID.randomUUID();
        when(teacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(teacherId))
                .thenReturn(List.of(anEntry(teacherId, "17000.00", TODAY.minusDays(10), NOW)));

        SalaryAsOfAnswer answer = service.salaryAsOf(teacherId, TODAY.plusDays(365));

        assertThat(answer.state()).isEqualTo(SalaryAsOfAnswer.State.RECORDED);
        assertThat(answer.amount()).isEqualByComparingTo("17000.00");
    }
}
