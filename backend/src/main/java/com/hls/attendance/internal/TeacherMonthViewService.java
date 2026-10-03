package com.hls.attendance.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.RollupView;
import com.hls.attendance.internal.RollupCalculator.DayKind;
import com.hls.attendance.internal.RollupCalculator.DayPlan;
import com.hls.attendance.internal.RollupCalculator.MarkFacts;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds one Teacher's month: a day entry per calendar day plus the rollup (spec 008 FR-010, FR-014). */
@Service
public class TeacherMonthViewService {

    /** Who is looking, which decides what they could edit. */
    public enum Viewer {
        SELF,
        SUPERVISOR
    }

    /** The state of one day in the month grid or calendar. */
    public enum DayState {
        MARKED,
        UNMARKED,
        NOT_PLACED,
        WEEKLY_OFF,
        NON_WORKING,
        FUTURE
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DayView(LocalDate date, DayState state, MarkView mark, String editableBy) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TeacherMonthView(
            UUID teacherId,
            String name,
            String month,
            boolean locked,
            String state,
            RollupView rollup,
            List<DayView> days) {}

    private final TeacherDirectory teachers;
    private final AttendanceMarkRepository marks;
    private final StatusCodeRepository codes;
    private final TeacherMonthRepository months;
    private final CalendarService calendar;
    private final MarkViewFactory markViews;
    private final BusinessCalendar business;

    public TeacherMonthViewService(
            TeacherDirectory teachers,
            AttendanceMarkRepository marks,
            StatusCodeRepository codes,
            TeacherMonthRepository months,
            CalendarService calendar,
            MarkViewFactory markViews,
            BusinessCalendar business) {
        this.teachers = teachers;
        this.marks = marks;
        this.codes = codes;
        this.months = months;
        this.calendar = calendar;
        this.markViews = markViews;
        this.business = business;
    }

    @Transactional(readOnly = true)
    public TeacherMonthView view(UUID teacherId, YearMonth month, Viewer viewer) {
        TeacherInfo teacher = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        if (teacher == null) {
            throw new NotFoundException("Teacher not found.");
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = business.today();

        List<PlacementSpan> placements = teachers.placementsOverlapping(List.of(teacherId), from, to);
        List<AttendanceMark> monthMarks = marks.findByTeacherIdAndMarkDateBetween(teacherId, from, to);
        Map<UUID, StatusCode> codeById =
                codes.findAll().stream().collect(Collectors.toMap(StatusCode::getId, Function.identity()));
        Map<LocalDate, MarkView> viewByDate = markViews.ofAll(monthMarks).stream()
                .collect(Collectors.toMap(MarkView::date, Function.identity()));
        Map<LocalDate, MarkFacts> facts = new HashMap<>();
        for (AttendanceMark m : monthMarks) {
            StatusCode code = codeById.get(m.getStatusCodeId());
            facts.put(m.getMarkDate(), new MarkFacts(code.getCategory(), m.getDayValue(), code.getWeight()));
        }
        Map<LocalDate, AttendanceMark> markByDate =
                monthMarks.stream().collect(Collectors.toMap(AttendanceMark::getMarkDate, Function.identity()));

        var rules = calendar.rules();
        Set<LocalDate> nonWorking = calendar.nonWorkingDates(from, to);
        TeacherMonth row = months.findByTeacherIdAndYearMonth(teacherId, month.toString()).orElse(null);
        boolean locked = row != null && row.getState() == MonthState.LOCKED;

        List<DayView> days = RollupCalculator.plan(month, placements, rules, nonWorking).stream()
                .map(plan -> dayOf(plan, viewByDate.get(plan.date()), markByDate.get(plan.date()), today, locked, viewer))
                .toList();

        Rollup rollup = locked
                ? row.frozenRollup()
                : RollupCalculator.compute(month, placements, facts, rules, nonWorking, today);
        return new TeacherMonthView(
                teacherId,
                teacher.name(),
                month.toString(),
                locked,
                row == null ? "OPEN" : row.getState().name(),
                toView(rollup, locked),
                days);
    }

    public static RollupView toView(Rollup r, boolean locked) {
        return new RollupView(
                r.workingDays(),
                r.daysWorked(),
                r.daysLeave(),
                r.trainingAvailable(),
                r.trainingAttended(),
                r.unmarked(),
                r.weightedTotal(),
                locked,
                locked);
    }

    private DayView dayOf(
            DayPlan plan, MarkView mark, AttendanceMark raw, LocalDate today, boolean locked, Viewer viewer) {
        LocalDate date = plan.date();
        DayState state;
        if (mark != null) {
            state = DayState.MARKED;
        } else if (plan.kind() == DayKind.NOT_PLACED) {
            state = DayState.NOT_PLACED;
        } else if (plan.kind() == DayKind.WEEKLY_OFF) {
            state = DayState.WEEKLY_OFF;
        } else if (plan.kind() == DayKind.NON_WORKING) {
            state = DayState.NON_WORKING;
        } else if (date.isAfter(today)) {
            state = DayState.FUTURE;
        } else {
            state = DayState.UNMARKED;
        }
        return new DayView(date, state, mark, editableBy(plan, raw, date, today, locked, viewer));
    }

    private String editableBy(
            DayPlan plan, AttendanceMark mark, LocalDate date, LocalDate today, boolean locked, Viewer viewer) {
        if (locked || !plan.placed() || date.isAfter(today)) {
            return "NONE";
        }
        if (viewer == Viewer.SUPERVISOR) {
            return "SUPERVISOR";
        }
        boolean inWindow = !date.isBefore(business.windowStart());
        boolean ownOrEmpty = mark == null || mark.getSetByKind() == SetByKind.SELF;
        return inWindow && ownOrEmpty ? "SELF" : "NONE";
    }
}
