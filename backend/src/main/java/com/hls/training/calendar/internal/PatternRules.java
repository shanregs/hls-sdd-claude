package com.hls.training.calendar.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Date maths of a standing pattern (spec 024 FR-004): nth weekday of a month, or a fixed day clamped to the month end. */
public final class PatternRules {

    public static final String NTH_WEEKDAY = "NTH_WEEKDAY";
    public static final String DAY_OF_MONTH = "DAY_OF_MONTH";
    /** nth value that means "the last". */
    public static final int LAST = 5;

    private PatternRules() {}

    /** One previewed month: the date and a note when the rule had to be adjusted. */
    public record Occurrence(LocalDate month, LocalDate date, String note) {}

    /** The session date of a month for the rule, or an error for a rule that is not valid. */
    public static LocalDate dateIn(YearMonth month, String ruleKind, Integer nth, Integer weekday, Integer dayOfMonth) {
        if (NTH_WEEKDAY.equals(ruleKind)) {
            if (nth == null || nth < 1 || nth > LAST || weekday == null || weekday < 1 || weekday > 7) {
                throw new IllegalArgumentException("Choose which weekday of the month.");
            }
            DayOfWeek dow = DayOfWeek.of(weekday);
            if (nth == LAST) {
                return month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(dow));
            }
            return month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, dow));
        }
        if (DAY_OF_MONTH.equals(ruleKind)) {
            if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) {
                throw new IllegalArgumentException("Choose a day of the month from 1 to 31.");
            }
            return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        }
        throw new IllegalArgumentException("Choose a rule.");
    }

    /** True when a fixed day does not exist in the month and was moved to its last day. */
    public static boolean clamped(YearMonth month, String ruleKind, Integer dayOfMonth) {
        return DAY_OF_MONTH.equals(ruleKind) && dayOfMonth != null && dayOfMonth > month.lengthOfMonth();
    }

    /** The next {@code months} occurrences from {@code startMonth} (first day of a month) with notes for the preview. */
    public static List<Occurrence> preview(
            LocalDate startMonth, int months, String ruleKind, Integer nth, Integer weekday, Integer dayOfMonth) {
        List<Occurrence> out = new ArrayList<>();
        YearMonth first = YearMonth.from(startMonth);
        for (int i = 0; i < months; i++) {
            YearMonth ym = first.plusMonths(i);
            LocalDate date = dateIn(ym, ruleKind, nth, weekday, dayOfMonth);
            String note = clamped(ym, ruleKind, dayOfMonth)
                    ? "Day " + dayOfMonth + " does not exist in " + ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                            + ", so the last day is used."
                    : null;
            out.add(new Occurrence(ym.atDay(1), date, note));
        }
        return out;
    }
}
