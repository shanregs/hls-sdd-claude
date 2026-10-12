package com.hls.settings.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One stored override of a runtime setting (spec 011 data-model.md). */
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected SystemSetting() {
        // JPA
    }

    SystemSetting(String settingKey, String value, Instant updatedAt, UUID updatedBy) {
        this.settingKey = settingKey;
        this.value = value;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    String getSettingKey() {
        return settingKey;
    }

    String getValue() {
        return value;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    UUID getUpdatedBy() {
        return updatedBy;
    }

    void change(String newValue, Instant at, UUID by) {
        this.value = newValue;
        this.updatedAt = at;
        this.updatedBy = by;
    }
}
