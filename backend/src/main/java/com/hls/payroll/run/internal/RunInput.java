package com.hls.payroll.run.internal;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Server-side checks and display formats shared by every write of the payroll run (spec 013b FR-018, FR-025,
 * Principle X). One place for the messages, so a rule reads the same wherever it is enforced. Dates are shown as
 * DD/MM/YYYY and rupees with Indian digit grouping.
 */
public final class RunInput {

    public static final int MAX_REASON = 500;
    public static final int MAX_REFERENCE = 100;

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    private RunInput() {}

    /** A required reason: plain text, trimmed, at most {@link #MAX_REASON} characters. */
    public static String requiredReason(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("A reason is required for " + what + ".");
        }
        return limited(raw.trim(), MAX_REASON, "The reason");
    }

    /** A reference: plain text, trimmed, at most {@link #MAX_REFERENCE} characters; blank is stored as none. */
    public static String reference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return limited(raw.trim(), MAX_REFERENCE, "The reference");
    }

    private static String limited(String text, int max, String what) {
        if (text.length() > max) {
            throw new InvalidInputException(what + " can be at most " + max + " characters.");
        }
        return text;
    }

    /** An adjustment amount: present, not zero, at most two decimals, within what a line can hold. */
    public static BigDecimal adjustmentAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidInputException("The amount is required.");
        }
        if (amount.signum() == 0) {
            throw new InvalidInputException("The amount cannot be zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("The amount can have at most two decimals.");
        }
        if (amount.abs().compareTo(new BigDecimal("9999999.99")) > 0) {
            throw new InvalidInputException("The amount cannot be more than " + rupees(new BigDecimal("9999999.99")) + ".");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** A month written as {@code yyyy-MM}. */
    public static YearMonth month(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("The month is required.");
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must be written as yyyy-MM, for example 2026-09.");
        }
    }

    /** A pay run covers a month that has ended: any month before the one {@code today} is in. */
    public static YearMonth endedMonth(YearMonth month, LocalDate today) {
        if (!month.isBefore(YearMonth.from(today))) {
            throw new InvalidInputException("The month has not ended: " + monthName(month) + " ends on "
                    + DISPLAY.format(month.atEndOfMonth()) + ".");
        }
        return month;
    }

    /** A payment date: present and not in the future. */
    public static LocalDate paymentDate(LocalDate date, LocalDate today) {
        if (date == null) {
            throw new InvalidInputException("The payment date is required.");
        }
        if (date.isAfter(today)) {
            throw new InvalidInputException("The payment date cannot be in the future (" + DISPLAY.format(today) + " is today).");
        }
        return date;
    }

    public static String date(LocalDate date) {
        return DISPLAY.format(date);
    }

    public static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** Rupees with Indian digit grouping and two decimals: {@code ₹1,25,000.50}; a negative amount gets a leading minus. */
    public static String rupees(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        String digits = scaled.abs().toPlainString();
        int dot = digits.indexOf('.');
        String whole = digits.substring(0, dot);
        String fraction = digits.substring(dot);
        StringBuilder grouped = new StringBuilder();
        int length = whole.length();
        if (length <= 3) {
            grouped.append(whole);
        } else {
            String lastThree = whole.substring(length - 3);
            String rest = whole.substring(0, length - 3);
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(rest.charAt(i));
            }
            grouped.append(',').append(lastThree);
        }
        return (scaled.signum() < 0 ? "-" : "") + "₹" + grouped + fraction;
    }
}
