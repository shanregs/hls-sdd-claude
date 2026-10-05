package com.hls.attendance.internal;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.attendance.internal.RollupCalculator.DayKind;
import com.hls.school.api.ConflictException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 009: the attendance side of leave. Business rules live here; leave only decides requests. */
@Service
class LeaveAttendanceImpl implements LeaveAttendance {

    private static final BigDecimal WHOLE = new BigDecimal("1.00");

    private final TeacherDirectory teachers;
    private final CalendarService calendar;
    private final BusinessCalendar businessCalendar;
    private final AttendanceMarkRepository marks;
    private final MarkHistoryRepository history;
    private final StatusCodeService codes;
    private final TeacherMonthLock monthLock;
    private final MarkService markService;

    LeaveAttendanceImpl(
            TeacherDirectory teachers,
            CalendarService calendar,
            BusinessCalendar businessCalendar,
            AttendanceMarkRepository marks,
            MarkHistoryRepository history,
            StatusCodeService codes,
            TeacherMonthLock monthLock,
            MarkService markService) {
        this.teachers = teachers;
        this.calendar = calendar;
        this.businessCalendar = businessCalendar;
        this.marks = marks;
        this.history = history;
        this.codes = codes;
        this.monthLock = monthLock;
        this.markService = markService;
    }

    @Override
    public LocalDate businessToday() {
        return businessCalendar.today();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveDay> workingDays(UUID teacherId, LocalDate from, LocalDate to) {
        List<PlacementSpan> placements = teachers.placementsOverlapping(List.of(teacherId), from, to);
        var rules = calendar.rules();
        Set<LocalDate> nonWorking = calendar.nonWorkingDates(from, to);
        List<LeaveDay> days = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            for (var plan : RollupCalculator.plan(month, placements, rules, nonWorking)) {
                if (plan.kind() == DayKind.WORKING && !plan.date().isBefore(from) && !plan.date().isAfter(to)) {
                    days.add(new LeaveDay(plan.date(), plan.schoolId(), WHOLE));
                }
            }
        }
        return days;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> problems(UUID teacherId, List<LeaveDay> days) {
        List<String> problems = new ArrayList<>();
        Set<YearMonth> months = new TreeSet<>(days.stream().map(d -> YearMonth.from(d.date())).collect(Collectors.toSet()));
        for (YearMonth month : months) {
            if (monthLock.isLocked(teacherId, month)) {
                problems.add("month locked: " + month);
            }
        }
        if (days.isEmpty()) {
            return problems;
        }
        LocalDate from = days.stream().map(LeaveDay::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = days.stream().map(LeaveDay::date).max(Comparator.naturalOrder()).orElseThrow();
        Set<LocalDate> covered = days.stream().map(LeaveDay::date).collect(Collectors.toSet());
        UUID leaveCode = codes.requireByShortCode("L").getId();
        List<String> supervisorSet = marks.findByTeacherIdAndMarkDateBetween(teacherId, from, to).stream()
                .filter(m -> covered.contains(m.getMarkDate()))
                .filter(m -> m.getSetByKind() == SetByKind.SUPERVISOR && !m.getStatusCodeId().equals(leaveCode))
                .map(m -> m.getMarkDate().toString())
                .sorted()
                .toList();
        if (!supervisorSet.isEmpty()) {
            problems.add("days set by a supervisor: " + String.join(", ", supervisorSet));
        }
        return problems;
    }

    @Override
    @Transactional
    public void apply(UUID requestId, UUID approverUserId, UUID teacherId, List<LeaveDay> days) {
        acquireLocks(teacherId, days.stream().map(d -> YearMonth.from(d.date())).collect(Collectors.toSet()));
        List<String> problems = problems(teacherId, days);
        if (!problems.isEmpty()) {
            throw new ConflictException("Leave cannot be applied: " + String.join("; ", problems) + ".");
        }
        for (LeaveDay day : days.stream().sorted(Comparator.comparing(LeaveDay::date)).toList()) {
            markService.setLeaveMark(approverUserId, teacherId, day.date(), day.schoolId(), day.value(), requestId);
        }
    }

    @Override
    @Transactional
    public RemoveResult remove(UUID requestId, UUID actorUserId) {
        List<AttendanceMark> current = marks.findByLeaveRequestId(requestId);
        List<MarkHistoryEntry> written = history.findByLeaveRequestId(requestId).stream()
                .filter(h -> h.getAction() != MarkAction.CLEARED)
                .toList();
        Map<UUID, Set<YearMonth>> monthsByTeacher = current.stream()
                .collect(Collectors.groupingBy(
                        AttendanceMark::getTeacherId,
                        Collectors.mapping(m -> YearMonth.from(m.getMarkDate()), Collectors.toSet())));
        monthsByTeacher.forEach(this::acquireLocks);
        for (AttendanceMark mark : current) {
            YearMonth month = YearMonth.from(mark.getMarkDate());
            if (monthLock.isLocked(mark.getTeacherId(), month)) {
                throw new ConflictException("Leave cannot be removed: month locked: " + month + ".");
            }
        }
        List<LocalDate> removed = new ArrayList<>();
        Set<LocalDate> removedSet = new HashSet<>();
        for (AttendanceMark mark : current.stream().sorted(Comparator.comparing(AttendanceMark::getMarkDate)).toList()) {
            markService.clearLeaveMark(actorUserId, mark, requestId);
            removed.add(mark.getMarkDate());
            removedSet.add(mark.getMarkDate());
        }
        List<LocalDate> kept = written.stream()
                .map(MarkHistoryEntry::getMarkDate)
                .distinct()
                .filter(d -> !removedSet.contains(d))
                .sorted()
                .toList();
        return new RemoveResult(removed, kept);
    }

    /** Takes the Teacher-month advisory locks in ascending month order so concurrent callers cannot deadlock. */
    private void acquireLocks(UUID teacherId, Set<YearMonth> months) {
        new TreeSet<>(months).forEach(month -> monthLock.acquire(teacherId, month));
    }
}
