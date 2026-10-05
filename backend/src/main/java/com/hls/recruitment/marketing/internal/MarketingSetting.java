package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A marketing setting; today only {@code mou_overdue_days}. */
@Entity
@Table(name = "marketing_setting")
public class MarketingSetting {

    public static final String MOU_OVERDUE_DAYS = "mou_overdue_days";

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "int_value")
    private Integer intValue;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MarketingSetting() {}

    public String getKey() {
        return key;
    }

    public Integer getIntValue() {
        return intValue;
    }

    public Long getVersion() {
        return version;
    }

    public void set(int value, UUID by, Instant at) {
        this.intValue = value;
        this.updatedBy = by;
        this.updatedAt = at;
    }
}
