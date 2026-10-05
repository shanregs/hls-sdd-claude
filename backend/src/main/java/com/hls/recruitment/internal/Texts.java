package com.hls.recruitment.internal;

import com.hls.school.api.InvalidInputException;

/** Small validation helpers shared by the recruitment services. */
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

    /** The last ten digits of a phone number; null when it has fewer than ten digits. */
    static String phoneKey(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() < 10 ? null : digits.substring(digits.length() - 10);
    }

    static String email(String value) {
        String cleaned = clean(value, 200, "Email");
        if (cleaned != null && !cleaned.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new InvalidInputException("Email is not valid.");
        }
        return cleaned;
    }
}
