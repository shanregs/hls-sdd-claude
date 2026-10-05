package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceMarkChanged;
import com.hls.attendance.api.MarkView;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, corrects and clears marks (spec 008 FR-001..FR-005, FR-017). Authorization and scope are
 * decided by the caller; this service enforces the business rules, keeps the history and audit trail.
 */
@Service
public class MarkService {

    private static final BigDecimal WHOLE = new BigDecimal("1.00");
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final int MAX_NOTE = 500;

    private final AttendanceMarkRepository marks;
    private final MarkHistoryRepository history;
    private final StatusCodeService codes;
    private final TeacherDirectory teachers;
    private final TeacherMonthLock monthLock;
    private final BusinessCalendar calendar;
    private final AttendanceAudit audit;
    private final MarkViewFactory views;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final AppUserRepository users;

    public MarkService(
            AttendanceMarkRepository marks,
            MarkHistoryRepository history,
            StatusCodeService codes,
            TeacherDirectory teachers,
            TeacherMonthLock monthLock,
            BusinessCalendar calendar,
            AttendanceAudit audit,
            MarkViewFactory views,
            Clock clock,
            ApplicationEventPublisher events,
            AppUserRepository users) {
        this.marks = marks;
        this.history = history;
        this.codes = codes;
        this.teachers = teachers;
        this.monthLock = monthLock;
        this.calendar = calendar;
        this.audit = audit;
        this.views = views;
        this.clock = clock;
        this.events = events;
        this.users = users;
    }

    @Transactional
    public MarkView setMark(
            UUID actorUserId,
            UUID teacherId,
            LocalDate date,
            String statusCode,
            BigDecimal dayValue,
            String note,
            Long version,
            SetByKind kind) {
        return apply(actorUserId, teacherId, date, statusCode, dayValue, note, version, kind, true);
    }

    /**
     * The same rules, history and audit as {@link #setMark}, but nobody is notified. For the dev demo seeder,
     * which writes months of marks at start-up and must not fill the Teacher's bell.
     */
    @Transactional
    public MarkView setMarkWithoutNotifying(
            UUID actorUserId,
            UUID teacherId,
            LocalDate date,
            String statusCode,
            BigDecimal dayValue,
            String note,
            Long version,
            SetByKind kind) {
        return apply(actorUserId, teacherId, date, statusCode, dayValue, note, version, kind, false);
    }

    private MarkView apply(
            UUID actorUserId,
            UUID teacherId,
            LocalDate date,
            String statusCode,
            BigDecimal dayValue,
            String note,
            Long version,
            SetByKind kind,
            boolean notify) {
        if (date == null) {
            throw new InvalidInputException("The date is required.");
        }
        BigDecimal value = requireDayValue(dayValue);
        String cleanNote = cleanNote(note);

        YearMonth month = YearMonth.from(date);
        monthLock.acquire(teacherId, month);

        TeacherInfo teacher = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        if (teacher == null) {
            throw new NotFoundException("Teacher not found.");
        }
        LocalDate today = calendar.today();
        if (date.isAfter(today)) {
            throw new ConflictException("You cannot mark a date in the future.");
        }
        if (kind == SetByKind.SELF && date.isBefore(calendar.windowStart())) {
            throw new ConflictException(
                    "This date is more than " + BusinessCalendar.SELF_MARK_WINDOW_DAYS + " days ago. Ask your Manager to record it.");
        }
        if (monthLock.isLocked(teacherId, month)) {
            throw new ConflictException("This month is locked. Attendance can only change after it is reopened.");
        }
        PlacementSpan placement = placementOn(teacherId, date, teacher);

        StatusCode code = codes.requireByShortCode(statusCode);
        if (!code.isActive()) {
            throw new ConflictException("The status " + code.getShortCode() + " is no longer in use.");
        }

        AttendanceMark existing = marks.findByTeacherIdAndMarkDate(teacherId, date).orElse(null);
        if (existing != null && version != null) {
            StaleVersion.check(AttendanceMark.class, existing.getId(), existing.getVersion(), version);
        }
        if (kind == SetByKind.SELF && existing != null && existing.getSetByKind() == SetByKind.SUPERVISOR) {
            throw new ConflictException("This day was set by your Manager. Ask them to correct it.");
        }
        if (existing != null
                && existing.getStatusCodeId().equals(code.getId())
                && existing.getDayValue().compareTo(value) == 0
                && java.util.Objects.equals(existing.getNote(), cleanNote)
                && existing.getSetByKind() == kind) {
            return views.of(existing);
        }

        String before = existing == null ? null : describe(existing);
        AttendanceMark mark = existing != null ? existing : new AttendanceMark(teacherId, date);
        mark.set(code.getId(), value, placement.schoolId(), cleanNote, actorUserId, kind, clock.instant());
        AttendanceMark saved = marks.saveAndFlush(mark);
        history.save(new MarkHistoryEntry(
                teacherId,
                date,
                existing == null ? MarkAction.CREATED : MarkAction.CORRECTED,
                code.getId(),
                value,
                placement.schoolId(),
                cleanNote,
                actorUserId,
                kind,
                saved.getSetAt()));
        audit.changed(actorUserId, AttendanceAudit.MARK, teacherId + ":" + date, "mark", before, code.getShortCode() + " " + value.toPlainString());
        if (notify && kind == SetByKind.SUPERVISOR) {
            publishChanged(actorUserId, teacherId, date);
        }
        return views.of(saved);
    }

    /**
     * Writes a Leave mark made by approved leave (spec 009). Unlike {@link #setMark} it accepts future
     * dates and ignores the self-mark window; the caller has already taken the Teacher-month locks and
     * validated the days, but the lock state is re-read here as a last line of defence.
     */
    @Transactional
    public void setLeaveMark(
            UUID approverUserId, UUID teacherId, LocalDate date, UUID schoolId, BigDecimal dayValue, UUID leaveRequestId) {
        YearMonth month = YearMonth.from(date);
        if (monthLock.isLocked(teacherId, month)) {
            throw new ConflictException("This month is locked. Attendance can only change after it is reopened.");
        }
        StatusCode code = codes.requireByShortCode("L");
        BigDecimal value = requireDayValue(dayValue);
        AttendanceMark existing = marks.findByTeacherIdAndMarkDate(teacherId, date).orElse(null);
        String before = existing == null ? null : describe(existing);
        AttendanceMark mark = existing != null ? existing : new AttendanceMark(teacherId, date);
        String note = "Leave request " + leaveRequestId;
        mark.set(code.getId(), value, schoolId, note, approverUserId, SetByKind.SUPERVISOR, clock.instant());
        mark.markFromLeave(leaveRequestId);
        AttendanceMark saved = marks.saveAndFlush(mark);
        history.save(new MarkHistoryEntry(
                teacherId,
                date,
                existing == null ? MarkAction.CREATED : MarkAction.CORRECTED,
                code.getId(),
                value,
                schoolId,
                note,
                approverUserId,
                SetByKind.SUPERVISOR,
                saved.getSetAt(),
                leaveRequestId));
        audit.changed(
                approverUserId,
                AttendanceAudit.MARK,
                teacherId + ":" + date,
                "mark",
                before,
                code.getShortCode() + " " + value.toPlainString() + " (leave request " + leaveRequestId + ")");
    }

    /** Removes a mark that approved leave made (spec 009 revoke or cancel); the caller holds the locks. */
    @Transactional
    public void clearLeaveMark(UUID actorUserId, AttendanceMark existing, UUID leaveRequestId) {
        YearMonth month = YearMonth.from(existing.getMarkDate());
        if (monthLock.isLocked(existing.getTeacherId(), month)) {
            throw new ConflictException("This month is locked. Attendance can only change after it is reopened.");
        }
        history.save(new MarkHistoryEntry(
                existing.getTeacherId(),
                existing.getMarkDate(),
                MarkAction.CLEARED,
                null,
                null,
                existing.getSchoolId(),
                null,
                actorUserId,
                SetByKind.SUPERVISOR,
                clock.instant(),
                leaveRequestId));
        audit.changed(
                actorUserId,
                AttendanceAudit.MARK,
                existing.getTeacherId() + ":" + existing.getMarkDate(),
                "mark",
                describe(existing) + " (leave request " + leaveRequestId + ")",
                null);
        marks.delete(existing);
        marks.flush();
    }

    @Transactional(readOnly = true)
    public boolean hasMark(UUID teacherId, LocalDate date) {
        return marks.findByTeacherIdAndMarkDate(teacherId, date).isPresent();
    }

    @Transactional
    public void clearMark(UUID actorUserId, UUID teacherId, LocalDate date) {
        if (date == null) {
            throw new InvalidInputException("The date is required.");
        }
        YearMonth month = YearMonth.from(date);
        monthLock.acquire(teacherId, month);
        if (monthLock.isLocked(teacherId, month)) {
            throw new ConflictException("This month is locked. Attendance can only change after it is reopened.");
        }
        AttendanceMark existing = marks.findByTeacherIdAndMarkDate(teacherId, date)
                .orElseThrow(() -> new NotFoundException("There is no mark on that date."));
        history.save(new MarkHistoryEntry(
                teacherId, date, MarkAction.CLEARED, null, null, existing.getSchoolId(), null, actorUserId, SetByKind.SUPERVISOR, clock.instant()));
        audit.changed(actorUserId, AttendanceAudit.MARK, teacherId + ":" + date, "mark", describe(existing), null);
        marks.delete(existing);
        marks.flush();
        publishChanged(actorUserId, teacherId, date);
    }

    /** Tells the notification module inside this transaction, so a refused change leaves no notification. */
    private void publishChanged(UUID actorUserId, UUID teacherId, LocalDate date) {
        String actorName = users.findById(actorUserId).map(AppUser::getDisplayName).orElse("A supervisor");
        events.publishEvent(new AttendanceMarkChanged(teacherId, date, actorUserId, actorName));
    }

    private PlacementSpan placementOn(UUID teacherId, LocalDate date, TeacherInfo teacher) {
        return teachers.placementsOverlapping(List.of(teacherId), date, date).stream()
                .filter(p -> p.covers(date))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "EXITED".equals(teacher.status())
                                ? "This Teacher has exited, so attendance cannot be marked on or after the exit date."
                                : "Attendance needs a School placement on that date."));
    }

    private String describe(AttendanceMark mark) {
        String shortCode = codes.require(mark.getStatusCodeId()).getShortCode();
        return shortCode + " " + mark.getDayValue().toPlainString();
    }

    private static BigDecimal requireDayValue(BigDecimal value) {
        if (value == null) {
            throw new InvalidInputException("Choose a whole day or a half day.");
        }
        if (value.compareTo(WHOLE) == 0) {
            return WHOLE;
        }
        if (value.compareTo(HALF) == 0) {
            return HALF;
        }
        throw new InvalidInputException("The day value must be 0.5 (half day) or 1 (whole day).");
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE) {
            throw new InvalidInputException("The note can be at most " + MAX_NOTE + " characters.");
        }
        return trimmed;
    }
}
