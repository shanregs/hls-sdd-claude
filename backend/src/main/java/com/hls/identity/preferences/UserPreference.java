package com.hls.identity.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row per user holding their personal theme choice (spec 039). No row means the defaults. */
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "theme_preset", nullable = false)
    private String themePreset;

    @Column(name = "theme_mode", nullable = false)
    private String themeMode;

    @Column(name = "custom_accent")
    private String customAccent;

    @Column(name = "sidebar_dark", nullable = false)
    private boolean sidebarDark;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {}

    public UserPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public String getThemePreset() { return themePreset; }
    public String getThemeMode() { return themeMode; }
    public String getCustomAccent() { return customAccent; }
    public boolean isSidebarDark() { return sidebarDark; }
    public Instant getChangedAt() { return changedAt; }

    public void apply(String preset, String mode, String accent, boolean sidebar, Instant changedAt, Instant now) {
        this.themePreset = preset;
        this.themeMode = mode;
        this.customAccent = accent;
        this.sidebarDark = sidebar;
        this.changedAt = changedAt;
        this.updatedAt = now;
    }
}
