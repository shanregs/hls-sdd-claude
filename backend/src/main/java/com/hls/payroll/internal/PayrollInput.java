package com.hls.payroll.internal;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Server-side checks shared by the salary and policy writes (spec 013a FR-003, FR-004, FR-007; Principle X). */
final class PayrollInput {

    static final int MAX_NOTE = 500;

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    private PayrollInput() {}

    /** An effective date is required and cannot reach back before the first day of the current business month. */
    static LocalDate effectiveDate(LocalDate date, LocalDate today) {
        if (date == null) {
            throw new InvalidInputException("The effective date is required.");
        }
        LocalDate earliest = today.withDayOfMonth(1);
        if (date.isBefore(earliest)) {
            throw new InvalidInputException(
                    "The effective date cannot be before the first day of the current month (" + DISPLAY.format(earliest)
                            + ").");
        }
        return date;
    }

    /** Plain text, trimmed, at most {@link #MAX_NOTE} characters; blank is stored as no note. */
    static String note(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String note = raw.trim();
        if (note.length() > MAX_NOTE) {
            throw new InvalidInputException("The note can be at most " + MAX_NOTE + " characters.");
        }
        return note;
    }

    /** The fixed decimal places of a value; trailing zeros do not count ({@code 12.50} has one). */
    static int decimals(BigDecimal value) {
        return Math.max(value.stripTrailingZeros().scale(), 0);
    }
}
