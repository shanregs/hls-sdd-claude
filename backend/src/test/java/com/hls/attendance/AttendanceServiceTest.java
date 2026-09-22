package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.attendance.api.AttendanceMonthLockedException;
import com.hls.attendance.api.AttendanceTeacherNotFoundException;
import com.hls.attendance.api.UnknownAttendanceStatusCodeException;
import com.hls.attendance.api.dto.AddNonWorkingDateRequest;
import com.hls.attendance.api.dto.AttendanceCategory;
import com.hls.attendance.api.dto.AttendanceGridView;
import com.hls.attendance.api.dto.AttendanceMarkView;
import com.hls.attendance.api.dto.AttendanceStatusCodeView;
import com.hls.attendance.api.dto.CreateStatusCodeRequest;
import com.hls.attendance.api.dto.EvidenceInput;
import com.hls.attendance.api.dto.GridCell;
import com.hls.attendance.api.dto.LockStatus;
import com.hls.attendance.api.dto.LockStatusView;
import com.hls.attendance.api.dto.MarkAttendanceRequest;
import com.hls.attendance.api.dto.MarkedByRole;
import com.hls.attendance.api.dto.MonthlyAttendanceRollupView;
import com.hls.attendance.api.dto.NonWorkingDateView;
import com.hls.attendance.internal.AttendanceMark;
import com.hls.attendance.internal.AttendanceMarkRepository;
import com.hls.attendance.internal.AttendanceNonWorkingDate;
import com.hls.attendance.internal.AttendanceNonWorkingDateRepository;
import com.hls.attendance.internal.AttendanceReopenRecord;
import com.hls.attendance.internal.AttendanceReopenRecordRepository;
import com.hls.attendance.internal.AttendanceService;
import com.hls.attendance.internal.AttendanceStatusCode;
import com.hls.attendance.internal.AttendanceStatusCodeRepository;
import com.hls.attendance.internal.AttendanceTeacherMonthLock;
import com.hls.attendance.internal.AttendanceTeacherMonthLockRepository;
import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditRecordRequest;
import com.hls.organization.api.AccountabilityQueries;
import com.hls.organization.api.dto.ItemType;
import com.hls.organization.api.dto.PortfolioItem;
import com.hls.teacher.api.TeacherQueries;
import com.hls.teacher.api.dto.TeacherProfileView;
import com.hls.teacher.api.dto.TeacherStatus;
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
 * Unit coverage for specs/011-attendance (FR-001-FR-025). Repositories,
 * {@link AuditWriter}, {@link TeacherQueries}, and {@link AccountabilityQueries}
 * are mocked; the real HTTP round trip and real Audit history read-back live
 * in {@code AttendanceIntegrationTest}.
 */
class AttendanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final String PERIOD = "2026-09";

    private AttendanceStatusCodeRepository statusCodeRepository;
    private AttendanceMarkRepository markRepository;
    private AttendanceTeacherMonthLockRepository lockRepository;
    private AttendanceReopenRecordRepository reopenRecordRepository;
    private AttendanceNonWorkingDateRepository nonWorkingDateRepository;
    private AuditWriter auditWriter;
    private TeacherQueries teacherQueries;
    private AccountabilityQueries accountabilityQueries;
    private Clock clock;
    private AttendanceService service;

    private final AttendanceStatusCode present = code("PRESENT", AttendanceCategory.WORKED, "1.00");
    private final AttendanceStatusCode leave = code("LEAVE", AttendanceCategory.LEAVE, "0.00");
    private final AttendanceStatusCode training = code("TRAINING", AttendanceCategory.TRAINING, "1.00");
    private final AttendanceStatusCode nonWorking = code("NON_WORKING", AttendanceCategory.NON_WORKING, "0.00");

    @BeforeEach
    void setUp() {
        statusCodeRepository = mock(AttendanceStatusCodeRepository.class);
        markRepository = mock(AttendanceMarkRepository.class);
        lockRepository = mock(AttendanceTeacherMonthLockRepository.class);
        reopenRecordRepository = mock(AttendanceReopenRecordRepository.class);
        nonWorkingDateRepository = mock(AttendanceNonWorkingDateRepository.class);
        auditWriter = mock(AuditWriter.class);
        teacherQueries = mock(TeacherQueries.class);
        accountabilityQueries = mock(AccountabilityQueries.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AttendanceService(statusCodeRepository, markRepository, lockRepository, reopenRecordRepository,
                nonWorkingDateRepository, auditWriter, teacherQueries, accountabilityQueries, clock);

        when(teacherQueries.exists(any())).thenReturn(true);
        when(statusCodeRepository.findById("PRESENT")).thenReturn(Optional.of(present));
        when(statusCodeRepository.findById("LEAVE")).thenReturn(Optional.of(leave));
        when(statusCodeRepository.findById("TRAINING")).thenReturn(Optional.of(training));
        when(statusCodeRepository.findById("NON_WORKING")).thenReturn(Optional.of(nonWorking));
        when(statusCodeRepository.findAll()).thenReturn(List.of(present, leave, training, nonWorking));
        when(nonWorkingDateRepository.findByActiveTrueAndDateBetween(any(), any())).thenReturn(List.of());
        when(lockRepository.findByTeacherIdAndPeriod(any(), any())).thenReturn(Optional.empty());
    }

    private static AttendanceStatusCode code(String code, AttendanceCategory category, String weight) {
        return new AttendanceStatusCode(code, code, category, new BigDecimal(weight), true, NOW, UUID.randomUUID());
    }

    private MarkAttendanceRequest request(LocalDate date, String statusCode, String fractionalValue) {
        return new MarkAttendanceRequest(date, UUID.randomUUID(), statusCode,
                fractionalValue == null ? null : new BigDecimal(fractionalValue), null);
    }

    // ---- User Story 1: markAttendance (self) -------------------------------------------

    @Test
    void markAttendance_createsNewMark_withGivenStatusAndFractionalValue() {
        UUID teacherId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.statusCode()).isEqualTo("PRESENT");
        assertThat(result.fractionalValue()).isEqualByComparingTo("1.00");
        verify(markRepository).save(any());
    }

    @Test
    void markAttendance_withHalfDayValue_0_5_savesFractionalValue() {
        UUID teacherId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", "0.5"), UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.fractionalValue()).isEqualByComparingTo("0.5");
    }

    @Test
    void markAttendance_withEvidence_savesGeoTagPhotoAndCheckinCode() {
        UUID teacherId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());
        EvidenceInput evidence = new EvidenceInput(new BigDecimal("12.9716"), new BigDecimal("77.5946"), "https://example.com/p.jpg", "ABC123");
        MarkAttendanceRequest req = new MarkAttendanceRequest(TODAY, UUID.randomUUID(), "PRESENT", null, evidence);

        AttendanceMarkView result = service.markAttendance(teacherId, req, UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.evidence().geoLat()).isEqualByComparingTo("12.9716");
        assertThat(result.evidence().checkinCode()).isEqualTo("ABC123");
    }

    @Test
    void markAttendance_withNoEvidence_savesSuccessfully() {
        UUID teacherId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.evidence().geoLat()).isNull();
        assertThat(result.evidence().checkinCode()).isNull();
    }

    @Test
    void markAttendance_sameTeacherAndDateAgain_updatesExistingRow_notADuplicate() {
        UUID teacherId = UUID.randomUUID();
        AttendanceMark existing = new AttendanceMark(UUID.randomUUID(), teacherId, TODAY, UUID.randomUUID(), "LEAVE",
                new BigDecimal("1.00"), null, UUID.randomUUID(), MarkedByRole.TEACHER, NOW.minusSeconds(3600));
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.of(existing));

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.statusCode()).isEqualTo("PRESENT");
        verify(markRepository).save(existing);
    }

    @Test
    void markAttendance_recordsAuditEntry_withActorRoleAndTimestamp() {
        UUID teacherId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        service.markAttendance(teacherId, request(TODAY, "PRESENT", null), actingUserId, MarkedByRole.TEACHER);

        verify(auditWriter).record(argThat((AuditRecordRequest r) ->
                r.sourceModule().equals("attendance") && r.entityType().equals("AttendanceMark")
                        && r.actorUserId().equals(actingUserId) && r.actorRole().equals("TEACHER")));
    }

    @Test
    void markAttendance_unknownStatusCode_throwsUnknownAttendanceStatusCodeException() {
        UUID teacherId = UUID.randomUUID();
        when(statusCodeRepository.findById("BOGUS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAttendance(teacherId, request(TODAY, "BOGUS", null), UUID.randomUUID(), MarkedByRole.TEACHER))
                .isInstanceOf(UnknownAttendanceStatusCodeException.class);
    }

    @Test
    void markAttendance_unknownTeacher_throwsAttendanceTeacherNotFoundException() {
        UUID teacherId = UUID.randomUUID();
        when(teacherQueries.exists(teacherId)).thenReturn(false);

        assertThatThrownBy(() -> service.markAttendance(teacherId, request(TODAY, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER))
                .isInstanceOf(AttendanceTeacherNotFoundException.class);
    }

    @Test
    void markAttendance_farInThePast_stillAccepted_whenTeacherMonthUnlocked() {
        UUID teacherId = UUID.randomUUID();
        LocalDate longAgo = TODAY.minusYears(1);
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, longAgo)).thenReturn(Optional.empty());
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, "2025-09")).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(longAgo, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER);

        assertThat(result.markDate()).isEqualTo(longAgo);
    }

    @Test
    void markAttendance_dateBeyondCurrentTeacherMonth_rejected() {
        UUID teacherId = UUID.randomUUID();
        LocalDate future = TODAY.plusMonths(2);

        assertThatThrownBy(() -> service.markAttendance(teacherId, request(future, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- User Story 2: Manager / Admin on-behalf marking -------------------------------

    @Test
    void markAttendance_asManager_attributesMarkedByAndRoleToTheManager() {
        UUID teacherId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", null), managerId, MarkedByRole.MANAGER);

        assertThat(result.markedBy()).isEqualTo(managerId);
        assertThat(result.markedByRole()).isEqualTo(MarkedByRole.MANAGER);
    }

    @Test
    void markAttendance_managerOverwritesTeacherSelfMark_newValueAttributedToManager_priorRetrievableViaAudit() {
        UUID teacherId = UUID.randomUUID();
        UUID teacherUserId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        AttendanceMark existing = new AttendanceMark(UUID.randomUUID(), teacherId, TODAY, UUID.randomUUID(), "PRESENT",
                new BigDecimal("1.00"), null, teacherUserId, MarkedByRole.TEACHER, NOW.minusSeconds(3600));
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.of(existing));

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "LEAVE", null), managerId, MarkedByRole.MANAGER);

        assertThat(result.markedBy()).isEqualTo(managerId);
        assertThat(result.markedByRole()).isEqualTo(MarkedByRole.MANAGER);
        verify(auditWriter).record(argThat((AuditRecordRequest r) -> r.beforeValue() != null && r.beforeValue().contains("PRESENT")));
    }

    @Test
    void markAttendance_asAdmin_attributesMarkedByAndRoleToAdmin() {
        UUID teacherId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(markRepository.findByTeacherIdAndMarkDate(teacherId, TODAY)).thenReturn(Optional.empty());

        AttendanceMarkView result = service.markAttendance(teacherId, request(TODAY, "PRESENT", null), adminId, MarkedByRole.ADMIN);

        assertThat(result.markedBy()).isEqualTo(adminId);
        assertThat(result.markedByRole()).isEqualTo(MarkedByRole.ADMIN);
    }

    // ---- User Story 3: rollupForMonth ---------------------------------------------------

    private AttendanceMark markOn(UUID teacherId, LocalDate date, String statusCode, String fractionalValue) {
        return new AttendanceMark(UUID.randomUUID(), teacherId, date, UUID.randomUUID(), statusCode,
                new BigDecimal(fractionalValue), null, UUID.randomUUID(), MarkedByRole.TEACHER, NOW);
    }

    @Test
    void rollupForMonth_daysWorked_sumsFractionalValueOverWorkedCategoryMarks() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 1), "PRESENT", "1.00"),
                markOn(teacherId, LocalDate.of(2026, 9, 2), "PRESENT", "0.5")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.daysWorked()).isEqualByComparingTo("1.5");
    }

    @Test
    void rollupForMonth_daysLeave_sumsFractionalValueOverLeaveCategoryMarks() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 5), "LEAVE", "1.00")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.daysLeave()).isEqualByComparingTo("1.00");
    }

    @Test
    void rollupForMonth_trainingDaysTotal_isCountOfTrainingCategoryMarks_attendedIsSumOfTheirFractionalValues() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 3), "TRAINING", "1.00"),
                markOn(teacherId, LocalDate.of(2026, 9, 4), "TRAINING", "0.5")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.trainingDaysTotal()).isEqualTo(2);
        assertThat(rollup.trainingDaysAttended()).isEqualByComparingTo("1.5");
    }

    @Test
    void rollupForMonth_nonWorkingDaysExcludedFromOverallWorkingDaysAndAllDenominators() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 6), "NON_WORKING", "1.00")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.overallWorkingDays()).isEqualTo(29);
        assertThat(rollup.unmarkedDays()).isEqualTo(29);
    }

    @Test
    void rollupForMonth_unmarkedDaysSurfacedSeparately_neverDefaultedToAStatus() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 1), "PRESENT", "1.00")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.unmarkedDays()).isEqualTo(29);
        assertThat(rollup.overallWorkingDays()).isEqualTo(30);
    }

    @Test
    void rollupForMonth_weightedAttendanceTotal_sumsFractionalValueTimesCodeWeight_excludingNonWorking() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of(
                markOn(teacherId, LocalDate.of(2026, 9, 1), "PRESENT", "1.00"),
                markOn(teacherId, LocalDate.of(2026, 9, 2), "LEAVE", "1.00"),
                markOn(teacherId, LocalDate.of(2026, 9, 3), "NON_WORKING", "1.00")));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.weightedAttendanceTotal()).isEqualByComparingTo("1.00");
    }

    @Test
    void rollupForMonth_reflectsAnAddedOrEditedOrRemovedMarkImmediately() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end))
                .thenReturn(List.of(markOn(teacherId, LocalDate.of(2026, 9, 1), "PRESENT", "1.00")))
                .thenReturn(List.of(markOn(teacherId, LocalDate.of(2026, 9, 1), "PRESENT", "1.00"),
                        markOn(teacherId, LocalDate.of(2026, 9, 2), "PRESENT", "1.00")));

        MonthlyAttendanceRollupView before = service.rollupForMonth(teacherId, PERIOD);
        MonthlyAttendanceRollupView after = service.rollupForMonth(teacherId, PERIOD);

        assertThat(before.daysWorked()).isEqualByComparingTo("1.00");
        assertThat(after.daysWorked()).isEqualByComparingTo("2.00");
    }

    @Test
    void rollupForMonth_calendarNonWorkingDate_excludedAutomatically_noMarkNeeded() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        LocalDate holiday = LocalDate.of(2026, 9, 28);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of());
        when(nonWorkingDateRepository.findByActiveTrueAndDateBetween(start, end)).thenReturn(List.of(
                new AttendanceNonWorkingDate(UUID.randomUUID(), holiday, "Holiday", true, NOW, UUID.randomUUID())));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.overallWorkingDays()).isEqualTo(29);
        assertThat(rollup.unmarkedDays()).isEqualTo(29);
    }

    @Test
    void rollupForMonth_explicitMarkOnCalendarNonWorkingDate_takesPrecedence() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        LocalDate holiday = LocalDate.of(2026, 9, 28);
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end))
                .thenReturn(List.of(markOn(teacherId, holiday, "PRESENT", "1.00")));
        when(nonWorkingDateRepository.findByActiveTrueAndDateBetween(start, end)).thenReturn(List.of(
                new AttendanceNonWorkingDate(UUID.randomUUID(), holiday, "Holiday", true, NOW, UUID.randomUUID())));

        MonthlyAttendanceRollupView rollup = service.rollupForMonth(teacherId, PERIOD);

        assertThat(rollup.daysWorked()).isEqualByComparingTo("1.00");
        assertThat(rollup.overallWorkingDays()).isEqualTo(30);
    }

    // ---- User Story 4: lock / reopen ----------------------------------------------------

    @Test
    void lockMonth_thenMarkAttendance_isRejected_withAttendanceMonthLockedException() {
        UUID teacherId = UUID.randomUUID();
        service.lockMonth(teacherId, PERIOD, UUID.randomUUID());
        AttendanceTeacherMonthLock lockedRow = new AttendanceTeacherMonthLock(UUID.randomUUID(), teacherId, PERIOD, LockStatus.LOCKED, NOW, UUID.randomUUID());
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, PERIOD)).thenReturn(Optional.of(lockedRow));

        assertThatThrownBy(() -> service.markAttendance(teacherId, request(TODAY, "PRESENT", null), UUID.randomUUID(), MarkedByRole.TEACHER))
                .isInstanceOf(AttendanceMonthLockedException.class);
    }

    @Test
    void reopenMonth_whenNotLocked_isRejected() {
        UUID teacherId = UUID.randomUUID();
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, PERIOD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenMonth(teacherId, PERIOD, "correction needed", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reopenMonth_makesTeacherMonthEditableAgain_appendsReopenRecord() {
        UUID teacherId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        AttendanceTeacherMonthLock lockedRow = new AttendanceTeacherMonthLock(lockId, teacherId, PERIOD, LockStatus.LOCKED, NOW, UUID.randomUUID());
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, PERIOD)).thenReturn(Optional.of(lockedRow));
        when(reopenRecordRepository.findByLockIdOrderByReopenedAtAsc(lockId)).thenReturn(List.of());

        LockStatusView result = service.reopenMonth(teacherId, PERIOD, "Teacher reported an error", UUID.randomUUID());

        assertThat(result.status()).isEqualTo(LockStatus.REOPENED);
        verify(reopenRecordRepository).save(argThat(r -> r.getReason().equals("Teacher reported an error")));
    }

    @Test
    void lockMonth_afterReopen_setsRelockedAtAndBy_fullSequenceRetrievableViaLockStatus() {
        UUID teacherId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        AttendanceTeacherMonthLock reopenedRow = new AttendanceTeacherMonthLock(lockId, teacherId, PERIOD, LockStatus.REOPENED, NOW.minusSeconds(600), UUID.randomUUID());
        AttendanceReopenRecord openReopen = new AttendanceReopenRecord(UUID.randomUUID(), lockId, "reason", NOW.minusSeconds(300), UUID.randomUUID());
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, PERIOD)).thenReturn(Optional.of(reopenedRow));
        when(reopenRecordRepository.findByLockIdAndRelockedAtIsNull(lockId)).thenReturn(Optional.of(openReopen));
        when(reopenRecordRepository.findByLockIdOrderByReopenedAtAsc(lockId)).thenReturn(List.of(openReopen));

        UUID directorId = UUID.randomUUID();
        LockStatusView result = service.lockMonth(teacherId, PERIOD, directorId);

        assertThat(result.status()).isEqualTo(LockStatus.LOCKED);
        verify(reopenRecordRepository).save(argThat(r -> r.getRelockedBy() != null && r.getRelockedBy().equals(directorId)));
    }

    // ---- User Story 5: grid --------------------------------------------------------------

    @Test
    void gridForAllTeachers_includesEveryTeacher_cellsMatchTheirIndividualMarks() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end))
                .thenReturn(List.of(markOn(teacherId, LocalDate.of(2026, 9, 5), "PRESENT", "1.00")));

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, true);

        assertThat(grid.rows()).hasSize(1);
        GridCell cell = grid.rows().get(0).cells().get(LocalDate.of(2026, 9, 5));
        assertThat(cell.statusCode()).isEqualTo("PRESENT");
    }

    @Test
    void gridForManager_includesOnlyThatManagersCurrentPortfolio() {
        UUID managerId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        when(accountabilityQueries.portfolioForManager(managerId)).thenReturn(List.of(
                new PortfolioItem(ItemType.TEACHER, teacherId, NOW),
                new PortfolioItem(ItemType.SCHOOL, UUID.randomUUID(), NOW)));
        when(teacherQueries.findById(teacherId)).thenReturn(Optional.of(
                new TeacherProfileView(teacherId, "Portfolio Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(any(), any(), any())).thenReturn(List.of());

        AttendanceGridView grid = service.gridForManager(managerId, PERIOD, true);

        assertThat(grid.rows()).hasSize(1);
        assertThat(grid.rows().get(0).teacherId()).isEqualTo(teacherId);
    }

    @Test
    void grid_dayWithNoMark_cellIsNull_neverDefaultedToAStatus() {
        UUID teacherId = UUID.randomUUID();
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(any(), any(), any())).thenReturn(List.of());

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, true);

        GridCell cell = grid.rows().get(0).cells().get(LocalDate.of(2026, 9, 10));
        assertThat(cell.statusCode()).isNull();
        assertThat(cell.category()).isNull();
    }

    @Test
    void grid_columnCount_matchesActualDaysInSelectedMonth() {
        when(teacherQueries.findAll()).thenReturn(List.of());

        assertThat(service.gridForAllTeachers("2026-09", true).days()).hasSize(30);
        assertThat(service.gridForAllTeachers("2026-02", true).days()).hasSize(28);
        assertThat(service.gridForAllTeachers("2028-02", true).days()).hasSize(29);
        assertThat(service.gridForAllTeachers("2026-01", true).days()).hasSize(31);
    }

    @Test
    void grid_calendarNonWorkingDate_cellShowsNonWorkingCategory_noStatusCode() {
        UUID teacherId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        LocalDate holiday = LocalDate.of(2026, 9, 28);
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end)).thenReturn(List.of());
        when(nonWorkingDateRepository.findByActiveTrueAndDateBetween(start, end)).thenReturn(List.of(
                new AttendanceNonWorkingDate(UUID.randomUUID(), holiday, "Holiday", true, NOW, UUID.randomUUID())));

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, true);

        GridCell cell = grid.rows().get(0).cells().get(holiday);
        assertThat(cell.category()).isEqualTo(AttendanceCategory.NON_WORKING);
        assertThat(cell.statusCode()).isNull();
    }

    @Test
    void grid_asAdmin_cellsAreEditableWhenUnlocked() {
        UUID teacherId = UUID.randomUUID();
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(any(), any(), any())).thenReturn(List.of());

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, true);

        assertThat(grid.rows().get(0).cells().get(LocalDate.of(2026, 9, 1)).editable()).isTrue();
    }

    @Test
    void grid_lockedTeacherMonth_cellsAreNotEditable_regardlessOfCallerCanEdit() {
        UUID teacherId = UUID.randomUUID();
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(any(), any(), any())).thenReturn(List.of());
        when(lockRepository.findByTeacherIdAndPeriod(teacherId, PERIOD)).thenReturn(Optional.of(
                new AttendanceTeacherMonthLock(UUID.randomUUID(), teacherId, PERIOD, LockStatus.LOCKED, NOW, UUID.randomUUID())));

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, true);

        assertThat(grid.rows().get(0).cells().get(LocalDate.of(2026, 9, 1)).editable()).isFalse();
    }

    @Test
    void grid_asDirector_cellsAreNeverEditable_evenWhenUnlocked() {
        UUID teacherId = UUID.randomUUID();
        when(teacherQueries.findAll()).thenReturn(List.of(
                new TeacherProfileView(teacherId, "A Teacher", "+9198", null, null, TeacherStatus.ACTIVE, NOW)));
        when(markRepository.findByTeacherIdAndMarkDateBetween(any(), any(), any())).thenReturn(List.of());

        AttendanceGridView grid = service.gridForAllTeachers(PERIOD, false);

        assertThat(grid.rows().get(0).cells().get(LocalDate.of(2026, 9, 1)).editable()).isFalse();
    }

    // ---- Polish: status codes / non-working calendar -----------------------------------

    @Test
    void createStatusCode_asAdmin_succeeds_andAppearsInListActiveCodes() {
        CreateStatusCodeRequest request = new CreateStatusCodeRequest("HALF_DAY_SPECIAL", "Half Day (Special)", AttendanceCategory.WORKED, new BigDecimal("0.50"));

        AttendanceStatusCodeView result = service.createStatusCode(request, UUID.randomUUID(), "ADMIN");

        assertThat(result.code()).isEqualTo("HALF_DAY_SPECIAL");
        assertThat(result.active()).isTrue();
        verify(statusCodeRepository).save(any());
    }

    @Test
    void addNonWorkingDate_asAdmin_succeeds_appearsInDatesForMonth() {
        LocalDate holiday = LocalDate.of(2026, 9, 28);
        AddNonWorkingDateRequest request = new AddNonWorkingDateRequest(holiday, "Gandhi Jayanti (observed)");

        NonWorkingDateView result = service.addNonWorkingDate(request, UUID.randomUUID());

        assertThat(result.date()).isEqualTo(holiday);
        assertThat(result.active()).isTrue();
        verify(auditWriter).record(argThat((AuditRecordRequest r) -> r.entityType().equals("AttendanceNonWorkingDate")));
    }

    @Test
    void deactivateNonWorkingDate_stopsItApplyingGoingForward_neverDeleted() {
        UUID id = UUID.randomUUID();
        AttendanceNonWorkingDate existing = new AttendanceNonWorkingDate(id, LocalDate.of(2026, 9, 28), "Holiday", true, NOW, UUID.randomUUID());
        when(nonWorkingDateRepository.findById(id)).thenReturn(Optional.of(existing));

        NonWorkingDateView result = service.deactivateNonWorkingDate(id, UUID.randomUUID());

        assertThat(result.active()).isFalse();
        verify(nonWorkingDateRepository).save(existing);
    }
}
