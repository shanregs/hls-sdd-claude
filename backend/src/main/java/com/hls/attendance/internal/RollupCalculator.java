package com.hls.attendance.internal;

import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rollup rules (spec 008 research.md section 4): no Spring, no database. Everything it needs
 * is passed in, so it is table-testable.
 *
 * <p>Exit is represented by the placement end date (exit minus one day), so a date after the exit is
 * simply not covered by any placement.
 */
public final class RollupCalculator {

    private RollupCalculator() {}

    /** What the Teacher's mark on a date means for the rollup. */
    public record MarkFacts(StatusCategory category, BigDecimal dayValue, BigDecimal weight) {}

    /** Weekly off days: the organization default plus per-School overrides (an override replaces it). */
    public record WeeklyOffRules(Set<DayOfWeek> defaults, Map<UUID, Set<DayOfWeek>> overrides) {

        public Set<DayOfWeek> forSchool(UUID schoolId) {
            Set<DayOfWeek> override = overrides.get(schoolId);
            return override != null ? override : defaults;
        }
    }

    /** Why a date is or is not a scheduled working day for a Teacher, before any mark is considered. */
    public enum DayKind {
        NOT_PLACED,
        WEEKLY_OFF,
        NON_WORKING,
        WORKING
    }

    /** One date of the month as the calendar and placements see it. */
    public record DayPlan(LocalDate date, DayKind kind, UUID schoolId) {

        public boolean placed() {
            return kind != DayKind.NOT_PLACED;
        }
    }

    /** Classifies every date of the month for one Teacher. */
    public static List<DayPlan> plan(
            YearMonth month,
            List<PlacementSpan> placements,
            WeeklyOffRules rules,
            Set<LocalDate> nonWorkingDates) {
        List<DayPlan> plans = new ArrayList<>(month.lengthOfMonth());
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            PlacementSpan placement = placements.stream()
                    .filter(p -> p.covers(date))
                    .findFirst()
                    .orElse(null);
            if (placement == null) {
                plans.add(new DayPlan(date, DayKind.NOT_PLACED, null));
            } else if (nonWorkingDates.contains(date)) {
                plans.add(new DayPlan(date, DayKind.NON_WORKING, placement.schoolId()));
            } else if (rules.forSchool(placement.schoolId()).contains(date.getDayOfWeek())) {
                plans.add(new DayPlan(date, DayKind.WEEKLY_OFF, placement.schoolId()));
            } else {
                plans.add(new DayPlan(date, DayKind.WORKING, placement.schoolId()));
            }
        }
        return plans;
    }

    /** The scheduled working days up to {@code today} that have no mark, oldest first. */
    public static List<LocalDate> unmarkedDates(
            YearMonth month,
            List<PlacementSpan> placements,
            Set<LocalDate> markedDates,
            WeeklyOffRules rules,
            Set<LocalDate> nonWorkingDates,
            LocalDate today) {
        return plan(month, placements, rules, nonWorkingDates).stream()
                .filter(p -> p.kind() == DayKind.WORKING)
                .map(DayPlan::date)
                .filter(d -> !d.isAfter(today) && !markedDates.contains(d))
                .toList();
    }

    public static Rollup compute(
            YearMonth month,
            List<PlacementSpan> placements,
            Map<LocalDate, MarkFacts> marks,
            WeeklyOffRules rules,
            Set<LocalDate> nonWorkingDates,
            LocalDate today) {
        BigDecimal workingDays = BigDecimal.ZERO;
        BigDecimal daysWorked = BigDecimal.ZERO;
        BigDecimal daysLeave = BigDecimal.ZERO;
        BigDecimal trainingAvailable = BigDecimal.ZERO;
        BigDecimal trainingAttended = BigDecimal.ZERO;
        BigDecimal weightedTotal = BigDecimal.ZERO;
        int unmarked = 0;

        for (DayPlan plan : plan(month, placements, rules, nonWorkingDates)) {
            MarkFacts mark = marks.get(plan.date());
            if (!plan.placed()) {
                // An induction day marked before any placement counts as training only (amendment A5).
                if (mark != null && mark.category() == StatusCategory.TRAINING) {
                    trainingAvailable = trainingAvailable.add(BigDecimal.ONE);
                    trainingAttended = trainingAttended.add(mark.dayValue());
                }
                continue;
            }
            boolean scheduled = plan.kind() == DayKind.WORKING;
            if (mark == null) {
                if (scheduled) {
                    workingDays = workingDays.add(BigDecimal.ONE);
                    if (!plan.date().isAfter(today)) {
                        unmarked++;
                    }
                }
                continue;
            }
            switch (mark.category()) {
                case WORKED -> {
                    workingDays = workingDays.add(BigDecimal.ONE);
                    daysWorked = daysWorked.add(mark.dayValue());
                    weightedTotal = weightedTotal.add(mark.dayValue().multiply(mark.weight()));
                }
                case TRAINING -> {
                    workingDays = workingDays.add(BigDecimal.ONE);
                    trainingAvailable = trainingAvailable.add(BigDecimal.ONE);
                    trainingAttended = trainingAttended.add(mark.dayValue());
                    weightedTotal = weightedTotal.add(mark.dayValue().multiply(mark.weight()));
                }
                case LEAVE -> {
                    if (scheduled) {
                        workingDays = workingDays.add(BigDecimal.ONE);
                    }
                    daysLeave = daysLeave.add(mark.dayValue());
                }
                case NON_WORKING -> {
                    // An explicit non-working mark removes the date from the working days.
                }
            }
        }
        return new Rollup(
                scale(workingDays),
                scale(daysWorked),
                scale(daysLeave),
                scale(trainingAvailable),
                scale(trainingAttended),
                unmarked,
                scale(weightedTotal));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
