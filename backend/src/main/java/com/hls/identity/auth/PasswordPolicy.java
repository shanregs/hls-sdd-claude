package com.hls.identity.auth;

/**
 * The one password rule (FR-017): at least {@value #MIN_LENGTH} characters and not equal to the
 * account's phone number. Shared by self-service reset and the admin-triggered reset (spec 004,
 * research.md section 2) so the two can never drift apart.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final String VIOLATION_MESSAGE = "Password must be at least 10 characters and not your phone number.";

    private PasswordPolicy() {}

    public static boolean isViolatedBy(String newPassword, String phone) {
        return newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.equals(phone);
    }
}
