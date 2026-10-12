package com.hls.attendance.internal;

import com.hls.attendance.api.MarkView;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The optional Principal confirmation of a day (amendment A2 to spec 008, decision D7). It never changes who marked the
 * day or what the mark says; it only records that the School's Principal confirmed (or did not confirm) it. Scope and
 * permission are decided by the caller.
 */
@Service
public class PrincipalConfirmationService {

    static final String FIELD = "principalConfirmation";
    private static final int MAX_NAME = 120;
    private static final int MAX_NOTE = 500;

    private final AttendanceMarkRepository marks;
    private final TeacherMonthLock monthLock;
    private final AttendanceAudit audit;
    private final MarkViewFactory views;
    private final Clock clock;

    public PrincipalConfirmationService(
            AttendanceMarkRepository marks,
            TeacherMonthLock monthLock,
            AttendanceAudit audit,
            MarkViewFactory views,
            Clock clock) {
        this.marks = marks;
        this.monthLock = monthLock;
        this.audit = audit;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public MarkView record(
            UUID actorUserId, UUID teacherId, LocalDate date, Boolean confirmed, String principalName, String note) {
        if (confirmed == null) {
            throw new InvalidInputException("Say whether the Principal confirmed the day.");
        }
        String name = clean(principalName, MAX_NAME, "The Principal's name");
        String cleanNote = clean(note, MAX_NOTE, "The note");
        AttendanceMark mark = lockedMark(teacherId, date);
        String before = describe(mark);
        mark.confirmByPrincipal(confirmed, name, cleanNote, actorUserId, clock.instant());
        AttendanceMark saved = marks.saveAndFlush(mark);
        audit.changed(actorUserId, AttendanceAudit.MARK, teacherId + ":" + date, FIELD, before, describe(saved));
        return views.of(saved);
    }

    @Transactional
    public MarkView clear(UUID actorUserId, UUID teacherId, LocalDate date) {
        AttendanceMark mark = lockedMark(teacherId, date);
        String before = describe(mark);
        mark.clearPrincipalConfirmation();
        AttendanceMark saved = marks.saveAndFlush(mark);
        audit.changed(actorUserId, AttendanceAudit.MARK, teacherId + ":" + date, FIELD, before, describe(saved));
        return views.of(saved);
    }

    private AttendanceMark lockedMark(UUID teacherId, LocalDate date) {
        if (date == null) {
            throw new InvalidInputException("The date is required.");
        }
        YearMonth month = YearMonth.from(date);
        monthLock.acquire(teacherId, month);
        AttendanceMark mark = marks.findByTeacherIdAndMarkDate(teacherId, date)
                .orElseThrow(() -> new NotFoundException("There is no mark on that date."));
        if (monthLock.isLocked(teacherId, month)) {
            throw new ConflictException("This month is locked. Attendance can only change after it is reopened.");
        }
        return mark;
    }

    private static String describe(AttendanceMark mark) {
        if (mark.getPrincipalConfirmed() == null) {
            return null;
        }
        return (mark.getPrincipalConfirmed() ? "confirmed" : "not confirmed")
                + (mark.getPrincipalName() == null ? "" : " by " + mark.getPrincipalName());
    }

    private static String clean(String value, int max, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(what + " can be at most " + max + " characters.");
        }
        return trimmed;
    }
}
