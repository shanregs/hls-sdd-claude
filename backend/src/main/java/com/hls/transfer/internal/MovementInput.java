package com.hls.transfer.internal;

import com.hls.school.api.InvalidInputException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Input rules and plain-words messages in one place (spec 027 FR-003): trimmed text, length limits, dates DD/MM/YYYY. */
final class MovementInput {

    static final int MAX_TEXT = 1000;
    static final Set<String> CATEGORIES =
            Set.of("SCHOOL_REQUEST", "TEACHER_REQUEST", "MANAGER_REBALANCING", "AFTER_REVIEW", "OTHER");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private MovementInput() {}

    static String date(LocalDate date) {
        return date == null ? "" : date.format(DMY);
    }

    /** Trimmed plain text of at most 1000 characters, or null when blank. */
    static String text(String value, String what) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new InvalidInputException(what + " can be at most " + MAX_TEXT + " characters.");
        }
        return trimmed;
    }

    static String category(String value) {
        if (value == null || !CATEGORIES.contains(value.trim().toUpperCase())) {
            throw new InvalidInputException("Choose a reason category.");
        }
        return value.trim().toUpperCase();
    }

    /** The note is required for Other and for a School-initiated request. */
    static String note(String category, String note) {
        String clean = text(note, "The note");
        if (clean == null && ("OTHER".equals(category) || "SCHOOL_REQUEST".equals(category))) {
            throw new InvalidInputException("A note is required for this reason.");
        }
        return clean;
    }

    /** A reason for a rejection or cancellation is required. */
    static String requiredReason(String value, String action) {
        String clean = text(value, "The reason");
        if (clean == null) {
            throw new InvalidInputException("Give a reason for the " + action + ".");
        }
        return clean;
    }
}
