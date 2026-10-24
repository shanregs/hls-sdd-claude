package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.RollupView;
import com.hls.attendance.internal.TeacherMonthViewService.DayView;
import com.hls.attendance.internal.TeacherMonthViewService.TeacherMonthView;
import com.hls.attendance.internal.TeacherMonthViewService.Viewer;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The module's public read contract, answered by the same services the screens use. */
@Service
class AttendanceReadApiImpl implements AttendanceReadApi {

    private static final RollupView NONE = new RollupView(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, false, false);

    private final TeacherMonthViewService views;
    private final TeacherMonthLock monthLock;
    private final TeacherDirectory teachers;
    private final AttendanceMarkRepository marks;
    private final StatusCodeCatalog codes;

    AttendanceReadApiImpl(
            TeacherMonthViewService views,
            TeacherMonthLock monthLock,
            TeacherDirectory teachers,
            AttendanceMarkRepository marks,
            StatusCodeCatalog codes) {
        this.views = views;
        this.monthLock = monthLock;
        this.teachers = teachers;
        this.marks = marks;
        this.codes = codes;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<UUID> teachersAwayOn(java.util.Collection<UUID> teacherIds, java.time.LocalDate date) {
        if (teacherIds.isEmpty()) {
            return java.util.Set.of();
        }
        return marks.findByTeacherIdInAndMarkDateBetween(teacherIds, date, date).stream()
                .filter(m -> codes.find(m.getStatusCodeId())
                        .map(c -> c.getCategory() == StatusCategory.LEAVE)
                        .orElse(false))
                .map(AttendanceMark::getTeacherId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public RollupView rollupOf(UUID teacherId, YearMonth month) {
        return month(teacherId, month).map(TeacherMonthView::rollup).orElse(NONE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarkView> marksOf(UUID teacherId, YearMonth month) {
        return month(teacherId, month)
                .map(v -> v.days().stream().map(DayView::mark).filter(Objects::nonNull).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocked(UUID teacherId, YearMonth month) {
        return monthLock.isLocked(teacherId, month);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<UUID> lockedTeachers(java.util.Collection<UUID> teacherIds, YearMonth month) {
        if (teacherIds.isEmpty()) {
            return java.util.Set.of();
        }
        return monthLock.lockedAmong(teacherIds, month);
    }

    /** Checked up front rather than by catching a not-found exception, which would poison the caller's transaction. */
    private Optional<TeacherMonthView> month(UUID teacherId, YearMonth month) {
        if (teachers.teacherInfo(List.of(teacherId)).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(views.view(teacherId, month, Viewer.SUPERVISOR));
    }
}
