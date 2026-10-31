package com.hls.incentive.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The single row that records when the incentive programme went live: approvals before it earn nothing (spec 030 D-3). */
@Entity
@Table(name = "incentive_program")
public class IncentiveProgram {

    @Id
    private Short id;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    protected IncentiveProgram() {
        // JPA
    }

    public Instant getStartsAt() {
        return startsAt;
    }
}
