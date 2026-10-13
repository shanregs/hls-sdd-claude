package com.hls.attendance.workingdays.internal;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The pure rules for a monthly working days figure (spec 037 FR-003, FR-029): 0 up to the number of days in that
 * calendar month, in whole or half days, and a window of five years either side of today for a new row. No repository,
 * no clock.
 */
public final class WorkingDaysRules {

    /** Years either side of today within which a new row (or a copied row) is accepted. */
    public static final int YEAR_WINDOW = 5;

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private WorkingDaysRules() {}

    /** Checks the figure for the month and returns it with one decimal; throws {@link InvalidInputException}. */
    public static BigDecimal checkFigure(BigDecimal value, int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidInputException("Month must be 1 to 12.");
        }
        int days = YearMonth.of(year, month).lengthOfMonth();
        String range = "Working days for " + monthName(month) + " " + year + " must be 0 to " + days + ", in half days.";
        if (value == null) {
            throw new InvalidInputException(range);
        }
        boolean halfStep = value.multiply(TWO).stripTrailingZeros().scale() <= 0;
        if (!halfStep || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(days)) > 0) {
            throw new InvalidInputException(range);
        }
        return value.setScale(1);
    }

    /** A new row may only be for a year from {@code today.year - 5} to {@code today.year + 5}. */
    public static void checkNewYear(int year, LocalDate today) {
        int from = today.getYear() - YEAR_WINDOW;
        int to = today.getYear() + YEAR_WINDOW;
        if (year < from || year > to) {
            throw new InvalidInputException("Year must be " + from + " to " + to + ".");
        }
    }

    public static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
