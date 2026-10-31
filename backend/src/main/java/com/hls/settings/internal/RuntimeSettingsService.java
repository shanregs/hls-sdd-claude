package com.hls.settings.internal;

import com.hls.audit.api.EntityChanged;
import com.hls.cache.SnapshotCache;
import com.hls.cache.SnapshotCaches;
import com.hls.identity.policy.RuntimeSettings;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.incentive.api.IncentiveSettings;
import com.hls.settings.internal.SettingKey.Group;
import com.hls.training.api.TrainingSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The runtime settings (spec 011): stored overrides over deployed properties over built-in defaults. It is also the
 * {@link RuntimeSettings} that {@code identity} reads, so every sign-in, OTP request and token issue sees an edit on
 * the next call. Overrides are held in a {@link SnapshotCache} that a save drops in its own transaction.
 */
@Service
public class RuntimeSettingsService implements RuntimeSettings, TrainingSettings, IncentiveSettings {

    public static final String SYSTEM_ENTITY = "SYSTEM_SETTING";
    public static final String SECURITY_ENTITY = "SECURITY_SETTING";

    record Stored(String value, Instant updatedAt, UUID updatedBy) {}

    public record SettingView(
            String key,
            String label,
            String unit,
            String type,
            int min,
            int max,
            @com.fasterxml.jackson.annotation.JsonProperty("default") String defaultValue,
            String value,
            boolean isDefault,
            Instant updatedAt,
            String updatedBy) {}

    public record GroupResponse(String group, List<SettingView> settings) {}

    /** Thrown when any value of a save is refused; carries a message per key. */
    public static class InvalidSettingsException extends RuntimeException {
        private final Map<String, String> fields;

        public InvalidSettingsException(Map<String, String> fields) {
            super("Some settings are not valid.");
            this.fields = fields;
        }

        public Map<String, String> fields() {
            return fields;
        }
    }

    private final SystemSettingRepository repository;
    private final SnapshotCache<Map<String, Stored>> overrides;
    private final Environment environment;
    private final ApplicationEventPublisher publisher;
    private final AppUserRepository users;
    private final Clock clock;

    RuntimeSettingsService(
            SystemSettingRepository repository,
            SnapshotCaches caches,
            Environment environment,
            ApplicationEventPublisher publisher,
            AppUserRepository users,
            Clock clock) {
        this.repository = repository;
        this.environment = environment;
        this.publisher = publisher;
        this.users = users;
        this.clock = clock;
        this.overrides = caches.create("systemSettings", () -> repository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SystemSetting::getSettingKey,
                        s -> new Stored(s.getValue(), s.getUpdatedAt(), s.getUpdatedBy()))));
    }

    // ------------------------------------------------------------------ effective values

    /** The second tier: the deployed property when it is a valid value, else the built-in default. */
    String defaultOf(SettingKey key) {
        if (key.property() != null) {
            String configured = environment.getProperty(key.property());
            if (configured != null) {
                var valid = key.normalize(configured);
                if (valid.isPresent()) {
                    return valid.get();
                }
            }
        }
        return key.builtInDefault();
    }

    String effective(SettingKey key) {
        Stored stored = overrides.get().get(key.key());
        if (stored != null) {
            var valid = key.normalize(stored.value());
            if (valid.isPresent()) {
                return valid.get();
            }
        }
        return defaultOf(key);
    }

    private int number(SettingKey key) {
        return Integer.parseInt(effective(key));
    }

    @Override
    public int passwordMinLength() {
        return number(SettingKey.PASSWORD_MIN_LENGTH);
    }

    @Override
    public int maxFailedAttempts() {
        return number(SettingKey.LOCKOUT_MAX_FAILED_ATTEMPTS);
    }

    @Override
    public int lockoutMinutes() {
        return number(SettingKey.LOCKOUT_DURATION_MINUTES);
    }

    @Override
    public int otpResendCooldownSeconds() {
        return number(SettingKey.OTP_RESEND_COOLDOWN_SECONDS);
    }

    @Override
    public int otpMaxConsecutiveRequests() {
        return number(SettingKey.OTP_MAX_CONSECUTIVE_REQUESTS);
    }

    @Override
    public int otpLockoutHours() {
        return number(SettingKey.OTP_LOCKOUT_HOURS);
    }

    @Override
    public int renewalTtlDays() {
        return number(SettingKey.SESSION_RENEWAL_TTL_DAYS);
    }

    @Override
    public int accessTokenTtlSeconds() {
        return number(SettingKey.SESSION_ACCESS_TOKEN_TTL_SECONDS);
    }

    @Override
    public String minAppVersion() {
        return effective(SettingKey.MOBILE_MIN_APP_VERSION);
    }

    @Override
    public int locationWaitSeconds() {
        return number(SettingKey.MOBILE_LOCATION_WAIT_SECONDS);
    }

    @Override
    public int locationReuseSeconds() {
        return number(SettingKey.MOBILE_LOCATION_REUSE_SECONDS);
    }

    @Override
    public int repeatThreshold() {
        return number(SettingKey.TRAINING_REPEAT_THRESHOLD);
    }

    @Override
    public int repeatWindowMonths() {
        return number(SettingKey.TRAINING_REPEAT_WINDOW_MONTHS);
    }

    @Override
    public int reminderDaysFirst() {
        return number(SettingKey.TRAINING_REMINDER_DAYS_FIRST);
    }

    @Override
    public int reminderDaysSecond() {
        return number(SettingKey.TRAINING_REMINDER_DAYS_SECOND);
    }

    @Override
    public int attendanceDueDays() {
        return number(SettingKey.TRAINING_ATTENDANCE_DUE_DAYS);
    }

    @Override
    public int generationHorizonMonths() {
        return number(SettingKey.TRAINING_GENERATION_HORIZON_MONTHS);
    }

    @Override
    public int amountPerSchool() {
        return number(SettingKey.INCENTIVE_AMOUNT);
    }

    // ------------------------------------------------------------------ screens

    @Transactional(readOnly = true)
    public GroupResponse view(Group group) {
        Map<String, Stored> stored = overrides.get();
        Map<UUID, String> names = names(stored.values().stream().map(Stored::updatedBy).collect(Collectors.toSet()));
        List<SettingView> views = SettingKey.of(group).stream()
                .map(key -> {
                    String value = effective(key);
                    String fallback = defaultOf(key);
                    Stored row = stored.get(key.key());
                    boolean usable = row != null && key.normalize(row.value()).isPresent();
                    UUID by = usable ? row.updatedBy() : null;
                    return new SettingView(
                            key.key(),
                            key.label(),
                            key.unit(),
                            key.type().name(),
                            key.min(),
                            key.max(),
                            fallback,
                            value,
                            value.equals(fallback),
                            usable ? row.updatedAt() : null,
                            by == null ? null : names.getOrDefault(by, "Unknown user"));
                })
                .toList();
        return new GroupResponse(group.name().toLowerCase(), views);
    }

    /**
     * Saves the given values of one group: all are checked first and a single bad one stores nothing. A value equal
     * to the current effective value is a no-op; each real change is audited in this transaction.
     */
    @Transactional
    public GroupResponse update(UUID actor, Group group, Map<String, String> values) {
        Map<String, String> problems = new LinkedHashMap<>();
        Map<SettingKey, String> accepted = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            throw new InvalidSettingsException(Map.of("values", "Nothing to save."));
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            var key = SettingKey.byKey(entry.getKey());
            if (key.isEmpty() || key.get().group() != group) {
                problems.put(entry.getKey(), "Unknown setting.");
                continue;
            }
            var normalized = key.get().normalize(entry.getValue());
            if (normalized.isEmpty()) {
                problems.put(entry.getKey(), key.get().problem());
            } else {
                accepted.put(key.get(), normalized.get());
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidSettingsException(problems);
        }

        Instant now = clock.instant();
        Map<String, String> before = new HashMap<>();
        accepted.keySet().forEach(k -> before.put(k.key(), effective(k)));
        boolean changed = false;
        for (Map.Entry<SettingKey, String> entry : accepted.entrySet()) {
            SettingKey key = entry.getKey();
            String previous = before.get(key.key());
            String next = entry.getValue();
            if (Objects.equals(previous, next)) {
                continue;
            }
            SystemSetting row = repository.findById(key.key()).orElse(null);
            if (row == null) {
                repository.save(new SystemSetting(key.key(), next, now, actor));
            } else {
                row.change(next, now, actor);
                repository.save(row);
            }
            publisher.publishEvent(new EntityChanged(
                    UUID.randomUUID(),
                    now,
                    actor,
                    group == Group.SYSTEM ? SYSTEM_ENTITY : SECURITY_ENTITY,
                    key.key(),
                    "value",
                    previous,
                    next));
            changed = true;
        }
        if (changed) {
            repository.flush();
            overrides.invalidateAround();
        }
        return view(group);
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        Set<UUID> present = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return present.isEmpty()
                ? Map.of()
                : users.findAllById(present).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
