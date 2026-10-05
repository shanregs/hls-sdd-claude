package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceMonthLocked;
import com.hls.attendance.api.AttendanceMonthReopened;
import com.hls.attendance.internal.RollupCalculator.MarkFacts;
import com.hls.attendance.internal.RollupCalculator.WeeklyOffRules;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks, reopens and relocks Teacher-months (spec 008 US6, FR-016..FR-019). A month can be locked only
 * after it has ended and only when every placed Teacher has every working day marked; the lock freezes
 * each Teacher's rollup. Every step takes the per-Teacher-month advisory lock first so a concurrent mark
 * cannot slip between the check and the freeze.
 */
@Service
public class MonthLockService {

    public record UnmarkedTeacher(UUID teacherId, String name, List<LocalDate> dates) {}

    /** The lock was refused because working days are unmarked; carries who and which days. */
    public static class UnmarkedDaysException extends RuntimeException {
        private final transient List<UnmarkedTeacher> unmarked;

        public UnmarkedDaysException(List<UnmarkedTeacher> unmarked) {
            super("Some working days are still unmarked. Mark them, then lock again.");
            this.unmarked = unmarked;
        }

        public List<UnmarkedTeacher> unmarked() {
            return unmarked;
        }
    }

    public record EventView(MonthEventType event, String reason, UUID actorUserId, java.time.Instant occurredAt) {}

    private record Calculation(Rollup rollup, List<LocalDate> unmarked) {}

    private final TeacherDirectory teachers;
    private final AttendanceMarkRepository marks;
    private final StatusCodeCatalog codes;
    private final TeacherMonthRepository months;
    private final TeacherMonthEventRepository events;
    private final CalendarService calendar;
    private final TeacherMonthLock monthLock;
    private final BusinessCalendar business;
    private final AttendanceAudit audit;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    public MonthLockService(
            TeacherDirectory teachers,
            AttendanceMarkRepository marks,
            StatusCodeCatalog codes,
            TeacherMonthRepository months,
            TeacherMonthEventRepository events,
            CalendarService calendar,
            TeacherMonthLock monthLock,
            BusinessCalendar business,
            AttendanceAudit audit,
            Clock clock,
            ApplicationEventPublisher publisher) {
        this.teachers = teachers;
        this.marks = marks;
        this.codes = codes;
        this.months = months;
        this.events = events;
        this.calendar = calendar;
        this.monthLock = monthLock;
        this.business = business;
        this.audit = audit;
        this.clock = clock;
        this.publisher = publisher;
    }

    /** Locks every Teacher-month of the month; returns how many were locked. */
    @Transactional
    public int lockMonth(UUID actorUserId, YearMonth month) {
        if (!business.monthHasEnded(month)) {
            throw new ConflictException("A month can only be locked after it has ended. "
                    + month.atEndOfMonth().plusDays(1) + " is the first day it can be locked.");
        }
        List<UUID> ids = teachers.teachersPlacedDuring(month.atDay(1), month.atEndOfMonth()).stream()
                .sorted()
                .toList();
        // Lock in a fixed order so two concurrent lock requests cannot deadlock.
        ids.forEach(id -> monthLock.acquire(id, month));

        Map<UUID, TeacherMonth> existing = months.findByTeacherIdInAndYearMonth(ids, month.toString()).stream()
                .collect(Collectors.toMap(TeacherMonth::getTeacherId, Function.identity()));
        List<UUID> toLock = ids.stream()
                .filter(id -> existing.get(id) == null || existing.get(id).getState() != MonthState.LOCKED)
                .toList();
        Map<UUID, Calculation> calculations = calculate(toLock, month);

        Map<UUID, TeacherInfo> infos = teachers.teacherInfo(toLock);
        List<UnmarkedTeacher> unmarked = new ArrayList<>();
        for (UUID id : toLock) {
            List<LocalDate> missing = calculations.get(id).unmarked();
            if (!missing.isEmpty()) {
                unmarked.add(new UnmarkedTeacher(id, infos.get(id).name(), missing));
            }
        }
        if (!unmarked.isEmpty()) {
            unmarked.sort(java.util.Comparator.comparing(UnmarkedTeacher::name));
            throw new UnmarkedDaysException(unmarked);
        }
        for (UUID id : toLock) {
            freeze(actorUserId, id, month, existing.get(id), calculations.get(id).rollup());
        }
        return toLock.size();
    }

    @Transactional
    public void reopen(UUID actorUserId, UUID teacherId, YearMonth month, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A reason is required to reopen a locked month.");
        }
        String cleanReason = reason.trim();
        if (cleanReason.length() > 500) {
            throw new InvalidInputException("The reason can be at most 500 characters.");
        }
        monthLock.acquire(teacherId, month);
        TeacherMonth row = months.findByTeacherIdAndYearMonth(teacherId, month.toString())
                .filter(m -> m.getState() == MonthState.LOCKED)
                .orElseThrow(() -> new ConflictException("This Teacher-month is not locked."));
        row.reopen(clock.instant());
        months.saveAndFlush(row);
        events.save(new TeacherMonthEvent(
                teacherId, month.toString(), MonthEventType.REOPENED, cleanReason, actorUserId, clock.instant()));
        audit.changed(actorUserId, AttendanceAudit.MONTH, teacherId + ":" + month, "state", "LOCKED", "OPEN: " + cleanReason);
        publisher.publishEvent(new AttendanceMonthReopened(teacherId, month, actorUserId, cleanReason));
    }

    @Transactional
    public Rollup relock(UUID actorUserId, UUID teacherId, YearMonth month) {
        monthLock.acquire(teacherId, month);
        TeacherMonth row = months.findByTeacherIdAndYearMonth(teacherId, month.toString())
                .filter(m -> m.getState() == MonthState.OPEN)
                .orElseThrow(() -> new ConflictException("This Teacher-month has not been reopened."));
        Calculation calc = calculate(List.of(teacherId), month).get(teacherId);
        if (!calc.unmarked().isEmpty()) {
            TeacherInfo info = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
            throw new UnmarkedDaysException(List.of(new UnmarkedTeacher(teacherId, info.name(), calc.unmarked())));
        }
        freeze(actorUserId, teacherId, month, row, calc.rollup());
        return calc.rollup();
    }

    @Transactional(readOnly = true)
    public List<EventView> events(UUID teacherId, YearMonth month) {
        if (teachers.teacherInfo(List.of(teacherId)).isEmpty()) {
            throw new NotFoundException("Teacher not found.");
        }
        return events.findByTeacherIdAndYearMonthOrderByOccurredAt(teacherId, month.toString()).stream()
                .map(e -> new EventView(e.getEvent(), e.getReason(), e.getActorUserId(), e.getOccurredAt()))
                .toList();
    }

    private void freeze(UUID actorUserId, UUID teacherId, YearMonth month, TeacherMonth existing, Rollup rollup) {
        boolean relock = existing != null;
        TeacherMonth row = relock ? existing : new TeacherMonth(teacherId, month.toString());
        row.lock(rollup, clock.instant());
        months.saveAndFlush(row);
        events.save(new TeacherMonthEvent(
                teacherId,
                month.toString(),
                relock ? MonthEventType.RELOCKED : MonthEventType.LOCKED,
                null,
                actorUserId,
                clock.instant()));
        audit.changed(
                actorUserId,
                AttendanceAudit.MONTH,
                teacherId + ":" + month,
                "state",
                relock ? "OPEN" : null,
                "LOCKED (worked " + rollup.daysWorked() + ", total " + rollup.weightedTotal() + ")");
        publisher.publishEvent(new AttendanceMonthLocked(teacherId, month, actorUserId));
    }

    /** The rollup and unmarked days for many Teachers, loading everything in bulk. */
    private Map<UUID, Calculation> calculate(Collection<UUID> teacherIds, YearMonth month) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = business.today();
        Map<UUID, List<PlacementSpan>> placements = teachers.placementsOverlapping(teacherIds, from, to).stream()
                .collect(Collectors.groupingBy(PlacementSpan::teacherId));
        Map<UUID, Map<LocalDate, AttendanceMark>> marksByTeacher = new HashMap<>();
        for (AttendanceMark m : marks.findByTeacherIdInAndMarkDateBetween(teacherIds, from, to)) {
            marksByTeacher.computeIfAbsent(m.getTeacherId(), k -> new HashMap<>()).put(m.getMarkDate(), m);
        }
        Map<UUID, StatusCode> codeById =
                codes.byId();
        WeeklyOffRules rules = calendar.rules();
        Set<LocalDate> nonWorking = calendar.nonWorkingDates(from, to);

        Map<UUID, Calculation> result = new HashMap<>();
        for (UUID id : teacherIds) {
            List<PlacementSpan> own = placements.getOrDefault(id, List.of());
            Map<LocalDate, AttendanceMark> ownMarks = marksByTeacher.getOrDefault(id, Map.of());
            Map<LocalDate, MarkFacts> facts = new HashMap<>();
            ownMarks.forEach((date, m) -> {
                StatusCode code = codeById.get(m.getStatusCodeId());
                facts.put(date, new MarkFacts(code.getCategory(), m.getDayValue(), code.getWeight()));
            });
            Rollup rollup = RollupCalculator.compute(month, own, facts, rules, nonWorking, today);
            List<LocalDate> unmarked = RollupCalculator.unmarkedDates(
                    month, own, new HashSet<>(ownMarks.keySet()), rules, nonWorking, today);
            result.put(id, new Calculation(rollup, unmarked));
        }
        return result;
    }
}
