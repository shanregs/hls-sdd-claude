package com.hls.expenses.internal;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Validation and normalisation of what a person types into a claim (spec 015 FR-001 to FR-005). */
final class ExpenseInput {

    static final int MAX_NOTE = 1000;
    static final int MAX_REASON = 500;
    static final int MAX_REFERENCE = 100;
    static final int MAX_AGE_DAYS = 60;

    private ExpenseInput() {}

    /** Plain text, trimmed; blank becomes null; over the limit is refused. */
    static String text(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new InvalidInputException(label + " can be at most " + max + " characters.");
        }
        return trimmed;
    }

    static String requiredText(String value, int max, String label) {
        String text = text(value, max, label);
        if (text == null) {
            throw new InvalidInputException(label + " is required.");
        }
        return text;
    }

    /** A positive amount with at most two decimals, returned with scale 2. */
    static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            throw new InvalidInputException("The amount is required.");
        }
        if (value.signum() <= 0) {
            throw new InvalidInputException("The amount must be more than zero.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("The amount can have at most two decimal places.");
        }
        return value.setScale(2);
    }

    /** A non-negative budget amount with at most two decimals. */
    static BigDecimal budget(BigDecimal value) {
        if (value == null) {
            throw new InvalidInputException("The amount is required.");
        }
        if (value.signum() < 0) {
            throw new InvalidInputException("The budget cannot be negative.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("The amount can have at most two decimal places.");
        }
        return value.setScale(2);
    }

    /** Submission rule of FR-005: not in the future and not older than 60 days. */
    static void checkDateWindow(LocalDate date, LocalDate today) {
        if (date == null) {
            throw new InvalidInputException("The expense date is required.");
        }
        if (date.isAfter(today)) {
            throw new InvalidInputException("The expense date cannot be in the future (date in future).");
        }
        if (date.isBefore(today.minusDays(MAX_AGE_DAYS))) {
            throw new InvalidInputException(
                    "The expense date is older than 60 days and can no longer be claimed (older than 60 days).");
        }
    }

    static ExpenseCategory category(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The category is required.");
        }
        try {
            return ExpenseCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown category " + value.trim() + ".");
        }
    }

    /** Who paid; the claimant when not stated. */
    static PaidBy paidBy(String value) {
        if (value == null || value.isBlank()) {
            return PaidBy.CLAIMANT;
        }
        try {
            return PaidBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Paid by must be CLAIMANT or COMPANY.");
        }
    }

    static PaidMode paidMode(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The payment mode is required.");
        }
        try {
            return PaidMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The payment mode must be CASH, BANK_TRANSFER, UPI or OTHER.");
        }
    }

    static UUID requestId(UUID value) {
        if (value == null) {
            throw new InvalidInputException("The request id is required.");
        }
        return value;
    }
}
