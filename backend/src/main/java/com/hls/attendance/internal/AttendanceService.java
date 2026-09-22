package com.hls.attendance.internal;

import com.hls.CorrelationIdFilter;
import com.hls.attendance.api.AttendanceGridQueries;
import com.hls.attendance.api.AttendanceLockCommands;
import com.hls.attendance.api.AttendanceLockQueries;
import com.hls.attendance.api.AttendanceMarkCommands;
import com.hls.attendance.api.AttendanceMarkQueries;
import com.hls.attendance.api.AttendanceMonthLockedException;
import com.hls.attendance.api.AttendanceNonWorkingCalendarCommands;
import com.hls.attendance.api.AttendanceNonWorkingCalendarQueries;
import com.hls.attendance.api.AttendanceRollupQueries;
import com.hls.attendance.api.AttendanceStatusCodeCommands;
import com.hls.attendance.api.AttendanceStatusCodeQueries;
import com.hls.attendance.api.AttendanceTeacherNotFoundException;
import com.hls.attendance.api.UnknownAttendanceStatusCodeException;
import com.hls.attendance.api.dto.AddNonWorkingDateRequest;
import com.hls.attendance.api.dto.AttendanceCategory;
import com.hls.attendance.api.dto.AttendanceGridRow;
import com.hls.attendance.api.dto.AttendanceGridView;
import com.hls.attendance.api.dto.AttendanceMarkView;
import com.hls.attendance.api.dto.AttendanceStatusCodeView;
import com.hls.attendance.api.dto.CreateStatusCodeRequest;
import com.hls.attendance.api.dto.GridCell;
import com.hls.attendance.api.dto.LockStatus;
import com.hls.attendance.api.dto.LockStatusView;
import com.hls.attendance.api.dto.MarkAttendanceRequest;
import com.hls.attendance.api.dto.MarkedByRole;
import com.hls.attendance.api.dto.MonthlyAttendanceRollupView;
import com.hls.attendance.api.dto.NonWorkingDateView;
import com.hls.attendance.api.dto.ReopenEntry;
import com.hls.audit.api.AuditWriter;
import com.hls.audit.api.dto.AuditAction;
import com.hls.audit.api.dto.AuditRecordRequest;
import com.hls.organization.api.AccountabilityQueries;
import com.hls.organization.api.dto.ItemType;
import com.hls.organization.api.dto.PortfolioItem;
import com.hls.teacher.api.TeacherQueries;
import com.hls.teacher.api.dto.TeacherProfileView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements every {@code attendance.api} interface in one class, mirroring
 * {@code TeacherService}'s/{@code ZoneService}'s own single-class reasoning.
 * Every write calls {@code audit.api.AuditWriter.record(...)} inside its own
 * transaction (FR-006).
 */
@Service
public class AttendanceService implements
        AttendanceStatusCodeQueries, AttendanceStatusCodeCommands,
        AttendanceMarkQueries, AttendanceMarkCommands,
        AttendanceRollupQueries,
        AttendanceLockQueries, AttendanceLockCommands,
        AttendanceGridQueries,
        AttendanceNonWorkingCalendarQueries, AttendanceNonWorkingCalendarCommands {

    private static final String ENTITY_MARK = "AttendanceMark";
    private static final String ENTITY_STATUS_CODE = "AttendanceStatusCode";
    private static final String ENTITY_LOCK = "AttendanceTeacherMonthLock";
    private static final String ENTITY_NON_WORKING_DATE = "AttendanceNonWorkingDate";

    private final AttendanceStatusCodeRepository statusCodeRepository;
    private final AttendanceMarkRepository markRepository;
    private final AttendanceTeacherMonthLockRepository lockRepository;
    private final AttendanceReopenRecordRepository reopenRecordRepository;
    private final AttendanceNonWorkingDateRepository nonWorkingDateRepository;
    private final AuditWriter auditWriter;
    private final TeacherQueries teacherQueries;
    private final AccountabilityQueries accountabilityQueries;
    private final Clock clock;

    public AttendanceService(
            AttendanceStatusCodeRepository statusCodeRepository,
            AttendanceMarkRepository markRepository,
            AttendanceTeacherMonthLockRepository lockRepository,
            AttendanceReopenRecordRepository reopenRecordRepository,
            AttendanceNonWorkingDateRepository nonWorkingDateRepository,
            AuditWriter auditWriter,
            TeacherQueries teacherQueries,
            AccountabilityQueries accountabilityQueries,
            Clock clock) {
        this.statusCodeRepository = statusCodeRepository;
        this.markRepository = markRepository;
        this.lockRepository = lockRepository;
        this.reopenRecordRepository = reopenRecordRepository;
        this.nonWorkingDateRepository = nonWorkingDateRepository;
        this.auditWriter = auditWriter;
        this.teacherQueries = teacherQueries;
        this.accountabilityQueries = accountabilityQueries;
        this.clock = clock;
    }

    // ---- Status codes (FR-005) ----------------------------------------------------------

    @Override
    public List<AttendanceStatusCodeView> listActiveCodes() {
        return statusCodeRepository.findByActiveTrue().stream().map(this::toStatusCodeView).toList();
    }

    @Override
    @Transactional
    public AttendanceStatusCodeView createStatusCode(CreateStatusCodeRequest request, UUID actingUserId, String actingRole) {
        AttendanceStatusCode code = new AttendanceStatusCode(
                request.code(), request.label(), request.category(), request.weight(), true, clock.instant(), actingUserId);
        statusCodeRepository.save(code);

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_STATUS_CODE, code.getCode(), AuditAction.CREATED,
                "Attendance status code created", null,
                "label=" + code.getLabel() + ", category=" + code.getCategory() + ", weight=" + code.getWeight(),
                actingUserId, actingRole, CorrelationIdFilter.currentCorrelationId()));

        return toStatusCodeView(code);
    }

    // ---- Marks (FR-001/FR-002/FR-003/FR-004/FR-006/FR-023/FR-024) ---------------------

    @Override
    public Optional<AttendanceMarkView> markForDate(UUID teacherId, LocalDate date) {
        return markRepository.findByTeacherIdAndMarkDate(teacherId, date).map(this::toMarkView);
    }

    @Override
    public List<AttendanceMarkView> marksForMonth(UUID teacherId, String period) {
        YearMonth ym = YearMonth.parse(period);
        return markRepository.findByTeacherIdAndMarkDateBetween(teacherId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(this::toMarkView)
                .toList();
    }

    @Override
    @Transactional
    public AttendanceMarkView markAttendance(UUID teacherId, MarkAttendanceRequest request, UUID actingUserId, MarkedByRole actingRole) {
        if (!teacherQueries.exists(teacherId)) {
            throw new AttendanceTeacherNotFoundException(teacherId);
        }
        AttendanceStatusCode statusCode = statusCodeRepository.findById(request.statusCode())
                .filter(AttendanceStatusCode::isActive)
                .orElseThrow(() -> new UnknownAttendanceStatusCodeException(request.statusCode()));

        LocalDate markDate = request.markDate();
        if (YearMonth.from(markDate).isAfter(YearMonth.now(clock))) {
            throw new IllegalArgumentException("Cannot mark a date beyond the current teacher-month: " + markDate);
        }
        String period = periodOf(markDate);
        lockRepository.findByTeacherIdAndPeriod(teacherId, period)
                .filter(lock -> lock.getStatus() == LockStatus.LOCKED)
                .ifPresent(lock -> {
                    throw new AttendanceMonthLockedException(teacherId, period);
                });

        BigDecimal fractionalValue = request.fractionalValue() != null ? request.fractionalValue() : new BigDecimal("1.00");
        Instant now = clock.instant();

        Optional<AttendanceMark> existing = markRepository.findByTeacherIdAndMarkDate(teacherId, markDate);
        String before = existing.map(this::describeMark).orElse(null);

        AttendanceMark mark;
        if (existing.isPresent()) {
            mark = existing.get();
            mark.update(request.schoolId(), statusCode.getCode(), fractionalValue, request.evidence(), actingUserId, actingRole, now);
        } else {
            mark = new AttendanceMark(
                    UUID.randomUUID(), teacherId, markDate, request.schoolId(), statusCode.getCode(),
                    fractionalValue, request.evidence(), actingUserId, actingRole, now);
        }
        markRepository.save(mark);

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_MARK, mark.getId().toString(),
                existing.isPresent() ? AuditAction.UPDATED : AuditAction.CREATED,
                "Attendance mark " + (existing.isPresent() ? "updated" : "created") + " for " + markDate,
                before, describeMark(mark), actingUserId, actingRole.name(), CorrelationIdFilter.currentCorrelationId()));

        return toMarkView(mark);
    }

    // ---- Rollup (FR-007/FR-008/FR-009/FR-010) ------------------------------------------

    @Override
    public MonthlyAttendanceRollupView rollupForMonth(UUID teacherId, String period) {
        YearMonth ym = YearMonth.parse(period);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        Map<String, AttendanceStatusCode> codesByCode = allStatusCodesByCode();
        Map<LocalDate, AttendanceMark> markByDate = markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end).stream()
                .collect(Collectors.toMap(AttendanceMark::getMarkDate, m -> m));
        Set<LocalDate> nonWorkingDates = activeNonWorkingDates(start, end);

        int overallWorkingDays = 0;
        int unmarkedDays = 0;
        int trainingDaysTotal = 0;
        BigDecimal trainingDaysAttended = BigDecimal.ZERO;
        BigDecimal daysWorked = BigDecimal.ZERO;
        BigDecimal daysLeave = BigDecimal.ZERO;
        BigDecimal weightedAttendanceTotal = BigDecimal.ZERO;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            AttendanceMark mark = markByDate.get(d);
            if (mark != null) {
                AttendanceStatusCode code = codesByCode.get(mark.getStatusCode());
                AttendanceCategory category = code.getCategory();
                if (category != AttendanceCategory.NON_WORKING) {
                    overallWorkingDays++;
                    weightedAttendanceTotal = weightedAttendanceTotal.add(mark.getFractionalValue().multiply(code.getWeight()));
                }
                switch (category) {
                    case WORKED -> daysWorked = daysWorked.add(mark.getFractionalValue());
                    case LEAVE -> daysLeave = daysLeave.add(mark.getFractionalValue());
                    case TRAINING -> {
                        trainingDaysTotal++;
                        trainingDaysAttended = trainingDaysAttended.add(mark.getFractionalValue());
                    }
                    case NON_WORKING -> {
                        // already excluded above
                    }
                }
            } else if (!nonWorkingDates.contains(d)) {
                overallWorkingDays++;
                unmarkedDays++;
            }
            // else: calendar non-working with no mark — excluded from both overallWorkingDays and unmarkedDays
        }

        LockStatus lockStatus = lockRepository.findByTeacherIdAndPeriod(teacherId, period)
                .map(AttendanceTeacherMonthLock::getStatus)
                .orElse(LockStatus.UNLOCKED);

        return new MonthlyAttendanceRollupView(
                teacherId, period, trainingDaysTotal, trainingDaysAttended, daysWorked, daysLeave,
                overallWorkingDays, unmarkedDays, weightedAttendanceTotal, lockStatus);
    }

    // ---- Lock / reopen (FR-011/FR-012/FR-013/FR-014) -----------------------------------

    @Override
    public LockStatusView lockStatus(UUID teacherId, String period) {
        return lockRepository.findByTeacherIdAndPeriod(teacherId, period)
                .map(this::toLockStatusView)
                .orElseGet(() -> new LockStatusView(teacherId, period, LockStatus.UNLOCKED, null, null, List.of()));
    }

    @Override
    @Transactional
    public LockStatusView lockMonth(UUID teacherId, String period, UUID actingUserId) {
        Instant now = clock.instant();
        Optional<AttendanceTeacherMonthLock> existing = lockRepository.findByTeacherIdAndPeriod(teacherId, period);
        boolean isRelock = existing.isPresent() && existing.get().getStatus() == LockStatus.REOPENED;

        AttendanceTeacherMonthLock lock = existing.orElseGet(
                () -> new AttendanceTeacherMonthLock(UUID.randomUUID(), teacherId, period, LockStatus.LOCKED, now, actingUserId));
        lock.lock(now, actingUserId);
        lockRepository.save(lock);

        if (isRelock) {
            reopenRecordRepository.findByLockIdAndRelockedAtIsNull(lock.getId()).ifPresent(record -> {
                record.relock(now, actingUserId);
                reopenRecordRepository.save(record);
            });
        }

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_LOCK, lock.getId().toString(),
                isRelock ? AuditAction.CORRECTED : AuditAction.UPDATED,
                isRelock ? "Teacher-month re-locked after correction" : "Teacher-month locked",
                null, "status=LOCKED", actingUserId, "DIRECTOR", CorrelationIdFilter.currentCorrelationId()));

        return toLockStatusView(lock);
    }

    @Override
    @Transactional
    public LockStatusView reopenMonth(UUID teacherId, String period, String reason, UUID actingUserId) {
        AttendanceTeacherMonthLock lock = lockRepository.findByTeacherIdAndPeriod(teacherId, period)
                .filter(l -> l.getStatus() == LockStatus.LOCKED)
                .orElseThrow(() -> new IllegalStateException("Teacher-month " + period + " is not currently locked"));
        lock.reopen();
        lockRepository.save(lock);

        Instant now = clock.instant();
        AttendanceReopenRecord record = new AttendanceReopenRecord(UUID.randomUUID(), lock.getId(), reason, now, actingUserId);
        reopenRecordRepository.save(record);

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_LOCK, lock.getId().toString(), AuditAction.CORRECTED,
                "Teacher-month reopened: " + reason, "status=LOCKED", "status=REOPENED",
                actingUserId, "DIRECTOR", CorrelationIdFilter.currentCorrelationId()));

        return toLockStatusView(lock);
    }

    // ---- Grid (User Story 5, FR-019/FR-020/FR-021/FR-025) ------------------------------

    @Override
    public AttendanceGridView gridForManager(UUID managerId, String period, boolean callerCanEdit) {
        List<UUID> teacherIds = accountabilityQueries.portfolioForManager(managerId).stream()
                .filter(item -> item.itemType() == ItemType.TEACHER)
                .map(PortfolioItem::itemId)
                .toList();
        return buildGrid(teacherIds, period, callerCanEdit);
    }

    @Override
    public AttendanceGridView gridForAllTeachers(String period, boolean callerCanEdit) {
        List<UUID> teacherIds = teacherQueries.findAll().stream().map(TeacherProfileView::id).toList();
        return buildGrid(teacherIds, period, callerCanEdit);
    }

    private AttendanceGridView buildGrid(List<UUID> teacherIds, String period, boolean callerCanEdit) {
        YearMonth ym = YearMonth.parse(period);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(d);
        }

        Map<String, AttendanceStatusCode> codesByCode = allStatusCodesByCode();
        Set<LocalDate> nonWorkingDates = activeNonWorkingDates(start, end);

        List<AttendanceGridRow> rows = new ArrayList<>();
        for (UUID teacherId : teacherIds) {
            String teacherName = teacherQueries.findById(teacherId).map(TeacherProfileView::name).orElse("Unknown");
            Map<LocalDate, AttendanceMark> markByDate = markRepository.findByTeacherIdAndMarkDateBetween(teacherId, start, end).stream()
                    .collect(Collectors.toMap(AttendanceMark::getMarkDate, m -> m));
            boolean locked = lockRepository.findByTeacherIdAndPeriod(teacherId, period)
                    .map(l -> l.getStatus() == LockStatus.LOCKED)
                    .orElse(false);
            boolean editable = callerCanEdit && !locked;

            Map<LocalDate, GridCell> cells = new LinkedHashMap<>();
            for (LocalDate d : days) {
                AttendanceMark mark = markByDate.get(d);
                if (mark != null) {
                    AttendanceStatusCode code = codesByCode.get(mark.getStatusCode());
                    cells.put(d, new GridCell(mark.getStatusCode(), code.getCategory(), mark.getFractionalValue(), mark.getSchoolId(), editable));
                } else if (nonWorkingDates.contains(d)) {
                    cells.put(d, new GridCell(null, AttendanceCategory.NON_WORKING, null, null, editable));
                } else {
                    cells.put(d, new GridCell(null, null, null, null, editable));
                }
            }
            rows.add(new AttendanceGridRow(teacherId, teacherName, cells));
        }

        return new AttendanceGridView(period, days, rows);
    }

    // ---- Non-Working Calendar (FR-022) -------------------------------------------------

    @Override
    public List<NonWorkingDateView> datesForMonth(String period) {
        YearMonth ym = YearMonth.parse(period);
        return nonWorkingDateRepository.findByActiveTrueAndDateBetween(ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(this::toNonWorkingDateView)
                .toList();
    }

    @Override
    @Transactional
    public NonWorkingDateView addNonWorkingDate(AddNonWorkingDateRequest request, UUID actingUserId) {
        AttendanceNonWorkingDate date = new AttendanceNonWorkingDate(
                UUID.randomUUID(), request.date(), request.label(), true, clock.instant(), actingUserId);
        nonWorkingDateRepository.save(date);

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_NON_WORKING_DATE, date.getId().toString(), AuditAction.CREATED,
                "Non-working date added: " + date.getLabel(), null,
                "date=" + date.getDate() + ", label=" + date.getLabel(), actingUserId, "ADMIN",
                CorrelationIdFilter.currentCorrelationId()));

        return toNonWorkingDateView(date);
    }

    @Override
    @Transactional
    public NonWorkingDateView deactivateNonWorkingDate(UUID id, UUID actingUserId) {
        AttendanceNonWorkingDate date = nonWorkingDateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No non-working date with id " + id));
        date.deactivate();
        nonWorkingDateRepository.save(date);

        auditWriter.record(new AuditRecordRequest(
                "attendance", ENTITY_NON_WORKING_DATE, date.getId().toString(), AuditAction.CORRECTED,
                "Non-working date deactivated: " + date.getLabel(), "active=true", "active=false",
                actingUserId, "ADMIN", CorrelationIdFilter.currentCorrelationId()));

        return toNonWorkingDateView(date);
    }

    // ---- helpers -------------------------------------------------------------------------

    private String periodOf(LocalDate date) {
        return YearMonth.from(date).toString();
    }

    private Map<String, AttendanceStatusCode> allStatusCodesByCode() {
        return statusCodeRepository.findAll().stream().collect(Collectors.toMap(AttendanceStatusCode::getCode, c -> c));
    }

    private Set<LocalDate> activeNonWorkingDates(LocalDate start, LocalDate end) {
        return nonWorkingDateRepository.findByActiveTrueAndDateBetween(start, end).stream()
                .map(AttendanceNonWorkingDate::getDate)
                .collect(Collectors.toSet());
    }

    private AttendanceMarkView toMarkView(AttendanceMark mark) {
        return new AttendanceMarkView(
                mark.getId(), mark.getTeacherId(), mark.getMarkDate(), mark.getSchoolId(), mark.getStatusCode(),
                mark.getFractionalValue(), mark.getEvidence(), mark.getMarkedBy(), mark.getMarkedByRole(), mark.getMarkedAt());
    }

    private AttendanceStatusCodeView toStatusCodeView(AttendanceStatusCode code) {
        return new AttendanceStatusCodeView(code.getCode(), code.getLabel(), code.getCategory(), code.getWeight(), code.isActive());
    }

    private NonWorkingDateView toNonWorkingDateView(AttendanceNonWorkingDate date) {
        return new NonWorkingDateView(date.getId(), date.getDate(), date.getLabel(), date.isActive());
    }

    private LockStatusView toLockStatusView(AttendanceTeacherMonthLock lock) {
        List<ReopenEntry> history = reopenRecordRepository.findByLockIdOrderByReopenedAtAsc(lock.getId()).stream()
                .map(r -> new ReopenEntry(r.getReason(), r.getReopenedAt(), r.getReopenedBy(), r.getRelockedAt(), r.getRelockedBy()))
                .toList();
        return new LockStatusView(lock.getTeacherId(), lock.getPeriod(), lock.getStatus(), lock.getLockedAt(), lock.getLockedBy(), history);
    }

    private String describeMark(AttendanceMark mark) {
        return "statusCode=" + mark.getStatusCode() + ", fractionalValue=" + mark.getFractionalValue() + ", schoolId=" + mark.getSchoolId();
    }
}
