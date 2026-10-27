package com.hls.identity.preferences;

import com.hls.identity.activity.ProfileUpdated;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A user's own theme (spec 039): read, replace (last change wins), reset, and a pure contrast check. */
@Service
public class UserThemeService {

    private static final Set<String> MODES = Set.of("light", "dark", "device");
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    /** Maps to 422 with a machine-readable code (and, for an unreadable accent, the verdict). */
    public static class ThemeInputException extends RuntimeException {
        private final String code;
        private final transient Map<String, Object> details;

        public ThemeInputException(String code, String message, Map<String, Object> details) {
            super(message);
            this.code = code;
            this.details = details;
        }

        public String code() { return code; }
        public Map<String, Object> details() { return details; }
    }

    public record ThemeRequest(String preset, String mode, String customAccent, Boolean sidebarDark,
                               String changedAt, String variant) {}

    private final UserPreferenceRepository repository;
    private final ThemeCatalogue catalogue;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserThemeService(UserPreferenceRepository repository, ThemeCatalogue catalogue,
                            ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.catalogue = catalogue;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID userId) {
        return view(repository.findById(userId).orElse(null), null);
    }

    @Transactional
    public Map<String, Object> put(UUID userId, ThemeRequest req) {
        String preset = req.preset() == null ? ThemeCatalogue.DEFAULT_PRESET : req.preset();
        if (catalogue.preset(preset).isEmpty()) {
            throw new ThemeInputException("UNKNOWN_PRESET", "Unknown theme preset.", Map.of());
        }
        String mode = req.mode() == null ? "device" : req.mode();
        if (!MODES.contains(mode)) {
            throw new ThemeInputException("INVALID_MODE", "Mode must be light, dark or device.", Map.of());
        }
        String accent = null;
        if (req.customAccent() != null && !req.customAccent().isBlank()) {
            if (!ContrastRules.isHex(req.customAccent())) {
                throw new ThemeInputException("INVALID_COLOUR", "Enter a colour like #1E5AA8.", Map.of());
            }
            accent = ContrastRules.normalise(req.customAccent());
            boolean dark = "dark".equals(req.variant());
            Map<String, Object> verdict = check(accent, preset, dark);
            if (!(Boolean) verdict.get("pass")) {
                throw new ThemeInputException("ACCENT_UNREADABLE", "That colour is hard to read.", verdict);
            }
        }
        Instant now = clock.instant();
        Instant changedAt = parse(req.changedAt(), now);
        UserPreference row = repository.findById(userId).orElse(null);
        if (row != null && row.getChangedAt().isAfter(changedAt)) {
            return view(row, false);
        }
        if (row == null) {
            row = new UserPreference(userId);
        }
        row.apply(preset, mode, accent, Boolean.TRUE.equals(req.sidebarDark()), changedAt, now);
        repository.saveAndFlush(row);
        events.publishEvent(new ProfileUpdated(
                UUID.randomUUID(), now, userId, userId, "theme (" + preset + ", " + mode + ")"));
        return view(row, true);
    }

    @Transactional
    public void reset(UUID userId) {
        repository.findById(userId).ifPresent(repository::delete);
        events.publishEvent(new ProfileUpdated(
                UUID.randomUUID(), clock.instant(), userId, userId, "theme (classic, device)"));
    }

    /** The verdict for one accent in one variant of a preset (pure, no write). */
    public Map<String, Object> check(String accent, String presetId, boolean dark) {
        Map<String, String> v = catalogue.variant(presetId, dark)
                .orElseGet(() -> catalogue.variant(ThemeCatalogue.DEFAULT_PRESET, dark).orElseThrow());
        String surface = v.get("surface");
        String background = v.get("background");
        String header = ContrastRules.headerTextOn(accent);
        double onSurface = ContrastRules.ratio(accent, surface);
        double onBackground = ContrastRules.ratio(accent, background);
        double headerRatio = ContrastRules.ratio(header, accent);
        List<Map<String, Object>> failures = new ArrayList<>();
        addFailure(failures, dark, "accent-on-surface", onSurface);
        addFailure(failures, dark, "accent-on-background", onBackground);
        addFailure(failures, dark, "header-text-on-accent", headerRatio);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("variant", dark ? "dark" : "light");
        out.put("pass", failures.isEmpty());
        out.put("ratios", Map.of(
                "accentOnSurface", ContrastRules.round2(onSurface),
                "accentOnBackground", ContrastRules.round2(onBackground),
                "headerTextOnAccent", ContrastRules.round2(headerRatio)));
        out.put("failures", failures);
        out.put("suggested", failures.isEmpty() ? accent : ContrastRules.adjust(accent, dark, surface, background));
        return out;
    }

    public Map<String, Object> contrastCheck(String accent, String presetId) {
        if (accent == null || !ContrastRules.isHex(accent)) {
            throw new ThemeInputException("INVALID_COLOUR", "Enter a colour like #1E5AA8.", Map.of());
        }
        String hex = ContrastRules.normalise(accent);
        String preset = presetId == null || catalogue.preset(presetId).isEmpty() ? ThemeCatalogue.DEFAULT_PRESET : presetId;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accent", hex);
        out.put("light", check(hex, preset, false));
        out.put("dark", check(hex, preset, true));
        return out;
    }

    private static void addFailure(List<Map<String, Object>> failures, boolean dark, String pair, double ratio) {
        if (ratio < ContrastRules.MIN_TEXT_RATIO) {
            failures.add(Map.of("variant", dark ? "dark" : "light", "pair", pair,
                    "ratio", ContrastRules.round2(ratio), "required", ContrastRules.MIN_TEXT_RATIO));
        }
    }

    private static Instant parse(String text, Instant now) {
        Instant limit = now.plus(CLOCK_SKEW);
        if (text == null || text.isBlank()) {
            return now;
        }
        try {
            Instant parsed = Instant.parse(text);
            return parsed.isAfter(limit) ? limit : parsed;
        } catch (DateTimeParseException e) {
            throw new ThemeInputException("INVALID_TIME", "changedAt must be an ISO instant.", Map.of());
        }
    }

    private Map<String, Object> view(UserPreference row, Boolean applied) {
        Map<String, Object> preference = new LinkedHashMap<>();
        preference.put("preset", row == null ? ThemeCatalogue.DEFAULT_PRESET : known(row.getThemePreset()));
        preference.put("mode", row == null ? "device" : row.getThemeMode());
        preference.put("customAccent", row == null ? null : row.getCustomAccent());
        preference.put("sidebarDark", row != null && row.isSidebarDark());
        preference.put("changedAt", row == null ? null : row.getChangedAt().toString());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preference", preference);
        out.put("isDefault", row == null);
        if (applied != null) {
            out.put("applied", applied);
        }
        out.put("catalogue", catalogue.document());
        return out;
    }

    private String known(String preset) {
        return catalogue.preset(preset).isPresent() ? preset : ThemeCatalogue.DEFAULT_PRESET;
    }
}
