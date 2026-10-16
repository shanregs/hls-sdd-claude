package com.hls.payroll.run.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One entry in a run's history: start, recompute, submit, return, approve, cancel, with the reason where there is one. */
@Entity
@Table(name = "payroll_run_event")
public class RunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "actor", nullable = false)
    private UUID actor;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "reason", length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;

    protected RunEvent() {
        // JPA
    }

    RunEvent(UUID runId, Instant at, UUID actor, String kind, String reason, String detail) {
        this.runId = runId;
        this.at = at;
        this.actor = actor;
        this.kind = kind;
        this.reason = reason;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getAt() {
        return at;
    }

    public UUID getActor() {
        return actor;
    }

    public String getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
