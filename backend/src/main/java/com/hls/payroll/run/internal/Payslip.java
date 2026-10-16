package com.hls.payroll.run.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The issued PDF of one paid line, fixed at approval (spec 013b US6). The bytes live in the shared file store. */
@Entity
@Table(name = "payroll_payslip")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "line_id", nullable = false, unique = true)
    private UUID lineId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected Payslip() {
        // JPA
    }

    Payslip(UUID lineId, UUID runId, UUID fileId, Instant issuedAt) {
        this.lineId = lineId;
        this.runId = runId;
        this.fileId = fileId;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLineId() {
        return lineId;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
