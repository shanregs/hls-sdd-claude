package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of the proposed MoU terms for a prospect; insert-only (a trigger enforces it too). */
@Entity
@Table(name = "proposal_revision")
public class ProposalRevision {

    @Id
    private UUID id;

    @Column(name = "prospect_id", nullable = false)
    private UUID prospectId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "teacher_count", nullable = false)
    private int teacherCount;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "salary_mode", nullable = false)
    private String salaryMode;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProposalRevision() {}

    public ProposalRevision(
            UUID prospectId,
            int revision,
            int teacherCount,
            LocalDate startMonth,
            String salaryMode,
            BigDecimal rate,
            String notes,
            UUID createdBy,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.prospectId = prospectId;
        this.revision = revision;
        this.teacherCount = teacherCount;
        this.startMonth = startMonth;
        this.salaryMode = salaryMode;
        this.rate = rate;
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProspectId() {
        return prospectId;
    }

    public int getRevision() {
        return revision;
    }

    public int getTeacherCount() {
        return teacherCount;
    }

    public LocalDate getStartMonth() {
        return startMonth;
    }

    public String getSalaryMode() {
        return salaryMode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
