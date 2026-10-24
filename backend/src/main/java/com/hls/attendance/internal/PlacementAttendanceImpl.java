package com.hls.attendance.internal;

import com.hls.attendance.api.PlacementAttendance;
import com.hls.school.api.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Attendance side of a Teacher move (spec 027): reads that refuse a move, and the re-tag of future leave marks. */
@Service
class PlacementAttendanceImpl implements PlacementAttendance {

    private final AttendanceMarkRepository marks;
    private final MarkHistoryRepository history;
    private final StatusCodeCatalog codes;
    private final TeacherMonthLock monthLock;
    private final AttendanceAudit audit;
    private final Clock clock;

    PlacementAttendanceImpl(
            AttendanceMarkRepository marks,
            MarkHistoryRepository history,
            StatusCodeCatalog codes,
            TeacherMonthLock monthLock,
            AttendanceAudit audit,
            Clock clock) {
        this.marks = marks;
        this.history = history;
        this.codes = codes;
        this.monthLock = monthLock;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasNonLeaveMarkOnOrAfter(UUID teacherId, LocalDate date) {
        Map<UUID, StatusCode> byId = codes.byId();
        return marks.findByTeacherIdAndMarkDateGreaterThanEqual(teacherId, date).stream()
                .anyMatch(m -> !isLeave(byId.get(m.getStatusCodeId())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean anyMonthLocked(UUID teacherId, LocalDate fromDate) {
        if (monthLock.isLocked(teacherId, YearMonth.from(fromDate))) {
            return true;
        }
        return marks.findByTeacherIdAndMarkDateGreaterThanEqual(teacherId, fromDate).stream()
                .map(m -> YearMonth.from(m.getMarkDate()))
                .distinct()
                .anyMatch(month -> monthLock.isLocked(teacherId, month));
    }

    @Override
    @Transactional
    public int retagLeaveMarks(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId) {
        Map<UUID, StatusCode> byId = codes.byId();
        List<AttendanceMark> leave = marks.findByTeacherIdAndMarkDateGreaterThanEqual(teacherId, from).stream()
                .filter(m -> isLeave(byId.get(m.getStatusCodeId())))
                .toList();
        for (AttendanceMark m : leave) {
            if (monthLock.isLocked(teacherId, YearMonth.from(m.getMarkDate()))) {
                throw new ConflictException("Attendance for " + YearMonth.from(m.getMarkDate()) + " is locked.");
            }
        }
        Instant now = clock.instant();
        int changed = 0;
        for (AttendanceMark m : leave) {
            if (newSchoolId.equals(m.getSchoolId())) {
                continue;
            }
            UUID before = m.getSchoolId();
            m.retag(newSchoolId);
            marks.save(m);
            history.save(new MarkHistoryEntry(
                    teacherId,
                    m.getMarkDate(),
                    MarkAction.CORRECTED,
                    m.getStatusCodeId(),
                    m.getDayValue(),
                    newSchoolId,
                    "Moved to a new School",
                    actorUserId,
                    SetByKind.SUPERVISOR,
                    now,
                    m.getLeaveRequestId()));
            audit.changed(actorUserId, AttendanceAudit.MARK, teacherId + ":" + m.getMarkDate(), "school", before, newSchoolId);
            changed++;
        }
        return changed;
    }

    private static boolean isLeave(StatusCode code) {
        return code != null && code.getCategory() == StatusCategory.LEAVE;
    }
}
