package com.hls.recruitment.marketing.internal;

import com.hls.school.api.InvalidInputException;
import java.util.Locale;

/** Small validation helpers for the marketing services. */
final class Texts {

    private Texts() {}

    static String clean(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
        return trimmed;
    }

    static String required(String value, int max, String label) {
        String cleaned = clean(value, max, label);
        if (cleaned == null) {
            throw new InvalidInputException(label + " is required.");
        }
        return cleaned;
    }

    static String email(String value) {
        String cleaned = clean(value, 200, "Email");
        if (cleaned != null && !cleaned.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new InvalidInputException("Email is not valid.");
        }
        return cleaned;
    }

    /** Lower case with every run of spaces collapsed, for the duplicate rule. */
    static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
