package com.hls.identity.user;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Normalizes Indian mobile numbers to one canonical 10-digit form regardless of how they were
 * entered ("+91 98765 43210", "09876543210", "9876543210", ...) so they compare equal (FR-004,
 * research.md §6). Deliberately narrow to this one country's numbering plan rather than a general
 * international library.
 */
@Component
public class PhoneNumberNormalizer {

    private static final Pattern NATIONAL_NUMBER = Pattern.compile("^[6-9]\\d{9}$");

    /**
     * @throws InvalidPhoneNumberException if the input is not a recognizable Indian mobile number
     */
    public String normalize(String rawInput) {
        if (rawInput == null) {
            throw new InvalidPhoneNumberException(rawInput);
        }
        String digitsAndPlus = rawInput.replaceAll("[\\s-]", "");
        String stripped;
        if (digitsAndPlus.startsWith("+91")) {
            stripped = digitsAndPlus.substring(3);
        } else if (digitsAndPlus.startsWith("91") && digitsAndPlus.length() == 12) {
            stripped = digitsAndPlus.substring(2);
        } else if (digitsAndPlus.startsWith("0") && digitsAndPlus.length() == 11) {
            stripped = digitsAndPlus.substring(1);
        } else {
            stripped = digitsAndPlus;
        }
        if (!NATIONAL_NUMBER.matcher(stripped).matches()) {
            throw new InvalidPhoneNumberException(rawInput);
        }
        return stripped;
    }

    /** Masks all but the first two and last two digits, for login-history/audit display (FR-019). */
    public String mask(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.length() != 10) {
            return "**********";
        }
        return normalizedPhone.substring(0, 2) + "******" + normalizedPhone.substring(8);
    }

    public static class InvalidPhoneNumberException extends RuntimeException {
        public InvalidPhoneNumberException(String rawInput) {
            super("Not a recognizable Indian mobile number: " + rawInput);
        }
    }
}
