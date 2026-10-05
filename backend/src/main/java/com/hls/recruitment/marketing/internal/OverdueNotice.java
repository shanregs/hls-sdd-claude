package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Remembers that a won prospect was reported overdue for a given limit, so it is not reported again. */
@Entity
@Table(name = "overdue_notice")
@IdClass(OverdueNotice.Key.class)
public class OverdueNotice {

    public record Key(UUID prospectId, int limitDays) implements Serializable {}

    @Id
    @Column(name = "prospect_id")
    private UUID prospectId;

    @Id
    @Column(name = "limit_days")
    private int limitDays;

    @Column(name = "noticed_at", nullable = false)
    private Instant noticedAt;

    protected OverdueNotice() {}

    public OverdueNotice(UUID prospectId, int limitDays, Instant noticedAt) {
        this.prospectId = prospectId;
        this.limitDays = limitDays;
        this.noticedAt = noticedAt;
    }

    public UUID getProspectId() {
        return prospectId;
    }

    public int getLimitDays() {
        return limitDays;
    }
}
