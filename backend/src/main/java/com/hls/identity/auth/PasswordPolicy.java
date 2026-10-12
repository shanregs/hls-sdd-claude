package com.hls.identity.auth;

/**
 * The one password rule (FR-017): at least a configured number of characters (Security Settings, spec 011) and not
 * equal to the account's phone number. Shared by self-service reset, the admin-triggered reset and the profile
 * password change so the rules can never drift apart.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static boolean isViolatedBy(String newPassword, String phone, int minLength) {
        return newPassword == null || newPassword.length() < minLength || newPassword.equals(phone);
    }

    public static String violationMessage(int minLength) {
        return "Password must be at least " + minLength + " characters and not your phone number.";
    }
}
