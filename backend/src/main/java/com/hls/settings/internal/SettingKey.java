package com.hls.settings.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The catalog of runtime settings (spec 011 data-model.md): key, group, label, unit, range, built-in default and the
 * deployed property that takes the place of the built-in default when set. Code, not data: a new setting is a new
 * constant.
 */
public enum SettingKey {
    MOBILE_MIN_APP_VERSION(
            "mobile.min-app-version", Group.SYSTEM, "Minimum Android app version", "x.y.z", Type.VERSION, 0, 0,
            "0.0.0", "hls.mobile.min-app-version"),
    MOBILE_LOCATION_WAIT_SECONDS(
            "mobile.location-wait-seconds", Group.SYSTEM, "Wait for a location fix", "seconds", Type.INTEGER, 1, 30,
            "4", "hls.mobile.location-wait-seconds"),
    MOBILE_LOCATION_REUSE_SECONDS(
            "mobile.location-reuse-seconds", Group.SYSTEM, "Reuse a recent location fix for", "seconds", Type.INTEGER,
            0, 300, "10", "hls.mobile.location-reuse-seconds"),
    TRAINING_REPEAT_THRESHOLD(
            "training.repeat-threshold", Group.SYSTEM, "Training absences that mark a repeat", "absences", Type.INTEGER, 1,
            10, "2", null),
    TRAINING_REPEAT_WINDOW_MONTHS(
            "training.repeat-window-months", Group.SYSTEM, "Months counted for repeat training absences", "months",
            Type.INTEGER, 1, 24, "6", null),
    TRAINING_REMINDER_DAYS_FIRST(
            "training.reminder-days-first", Group.SYSTEM, "First training reminder", "days before", Type.INTEGER, 0, 30,
            "2", null),
    TRAINING_REMINDER_DAYS_SECOND(
            "training.reminder-days-second", Group.SYSTEM, "Second training reminder (0 turns it off)", "days before",
            Type.INTEGER, 0, 30, "1", null),
    TRAINING_ATTENDANCE_DUE_DAYS(
            "training.attendance-due-days", Group.SYSTEM, "Days after a session to record attendance", "days",
            Type.INTEGER, 1, 60, "7", null),
    TRAINING_GENERATION_HORIZON_MONTHS(
            "training.generation-horizon-months", Group.SYSTEM, "Months of monthly sessions kept ahead", "months",
            Type.INTEGER, 1, 12, "3", null),
    INCENTIVE_AMOUNT(
            "incentive.amount-per-school", Group.SYSTEM, "Manager incentive per closed School", "Rupees", Type.INTEGER, 0,
            100000, "5000", null),

    PASSWORD_MIN_LENGTH(
            "password.min-length", Group.SECURITY, "Minimum password length", "characters", Type.INTEGER, 8, 64,
            "10", null),
    LOCKOUT_MAX_FAILED_ATTEMPTS(
            "lockout.max-failed-attempts", Group.SECURITY, "Failed sign-ins before lockout", "attempts", Type.INTEGER,
            3, 20, "5", null),
    LOCKOUT_DURATION_MINUTES(
            "lockout.duration-minutes", Group.SECURITY, "Lockout duration", "minutes", Type.INTEGER, 1, 1440, "30",
            null),
    OTP_RESEND_COOLDOWN_SECONDS(
            "otp.resend-cooldown-seconds", Group.SECURITY, "Wait between code requests", "seconds", Type.INTEGER, 10,
            600, "30", null),
    OTP_MAX_CONSECUTIVE_REQUESTS(
            "otp.max-consecutive-requests", Group.SECURITY, "Code requests before a request lockout", "requests",
            Type.INTEGER, 1, 20, "5", null),
    OTP_LOCKOUT_HOURS(
            "otp.lockout-hours", Group.SECURITY, "Code request lockout", "hours", Type.INTEGER, 1, 72, "4", null),
    SESSION_RENEWAL_TTL_DAYS(
            "session.renewal-ttl-days", Group.SECURITY, "Stay signed in for", "days", Type.INTEGER, 1, 90, "14",
            "hls.session.renewal-ttl-days"),
    SESSION_ACCESS_TOKEN_TTL_SECONDS(
            "session.access-token-ttl-seconds", Group.SECURITY, "Access token lifetime", "seconds", Type.INTEGER, 300,
            3600, "900", "hls.security.jwt.access-token-ttl-seconds");

    public enum Group {
        SYSTEM,
        SECURITY;

        public static Optional<Group> parse(String raw) {
            return raw == null
                    ? Optional.empty()
                    : Arrays.stream(values()).filter(g -> g.name().equalsIgnoreCase(raw.trim())).findFirst();
        }
    }

    public enum Type {
        INTEGER,
        VERSION
    }

    private static final Pattern VERSION = Pattern.compile("\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}");

    private final String key;
    private final Group group;
    private final String label;
    private final String unit;
    private final Type type;
    private final int min;
    private final int max;
    private final String builtInDefault;
    private final String property;

    SettingKey(
            String key,
            Group group,
            String label,
            String unit,
            Type type,
            int min,
            int max,
            String builtInDefault,
            String property) {
        this.key = key;
        this.group = group;
        this.label = label;
        this.unit = unit;
        this.type = type;
        this.min = min;
        this.max = max;
        this.builtInDefault = builtInDefault;
        this.property = property;
    }

    public String key() {
        return key;
    }

    public Group group() {
        return group;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public Type type() {
        return type;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public String builtInDefault() {
        return builtInDefault;
    }

    /** The deployed property that replaces the built-in default when set, or null. */
    public String property() {
        return property;
    }

    public static List<SettingKey> of(Group group) {
        return Arrays.stream(values()).filter(k -> k.group == group).toList();
    }

    public static Optional<SettingKey> byKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }

    /** The normalized text of {@code raw}, or empty when it is not a valid value for this setting. */
    public Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (type == Type.VERSION) {
            if (!VERSION.matcher(text).matches()) {
                return Optional.empty();
            }
            String[] parts = text.split("\\.");
            return Optional.of(Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]) + "." + Integer.parseInt(parts[2]));
        }
        if (!text.matches("\\d{1,9}")) {
            return Optional.empty();
        }
        int value = Integer.parseInt(text);
        return value >= min && value <= max ? Optional.of(Integer.toString(value)) : Optional.empty();
    }

    /** What to tell the user when {@code raw} is refused. */
    public String problem() {
        return type == Type.VERSION
                ? label + " must look like 1.2.0."
                : label + " must be a whole number from " + min + " to " + max + " " + unit + ".";
    }
}
