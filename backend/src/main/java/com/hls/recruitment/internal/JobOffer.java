package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An offer of work to a selected candidate. The terms are editable only while a DRAFT; once issued they never change
 * (a database trigger enforces it too), and a changed package is a new offer that supersedes this one.
 */
@Entity
@Table(name = "job_offer")
public class JobOffer {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "phone_key", nullable = false)
    private String phoneKey;

    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary;

    @Column(name = "allowances")
    private String allowances;

    @Column(name = "terms")
    private String terms;

    @Column(name = "expected_joining")
    private LocalDate expectedJoining;

    @Column(name = "offer_date", nullable = false)
    private LocalDate offerDate;

    @Column(name = "response_deadline", nullable = false)
    private LocalDate responseDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OfferStatus status = OfferStatus.DRAFT;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobOffer() {}

    public JobOffer(
            UUID candidateId,
            String phoneKey,
            String role,
            BigDecimal monthlySalary,
            String allowances,
            String terms,
            LocalDate expectedJoining,
            LocalDate offerDate,
            LocalDate responseDeadline,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.candidateId = candidateId;
        this.phoneKey = phoneKey;
        this.role = role;
        this.monthlySalary = monthlySalary;
        this.allowances = allowances;
        this.terms = terms;
        this.expectedJoining = expectedJoining;
        this.offerDate = offerDate;
        this.responseDeadline = responseDeadline;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getRole() {
        return role;
    }

    public String getPhoneKey() {
        return phoneKey;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public String getAllowances() {
        return allowances;
    }

    public String getTerms() {
        return terms;
    }

    public LocalDate getExpectedJoining() {
        return expectedJoining;
    }

    public LocalDate getOfferDate() {
        return offerDate;
    }

    public LocalDate getResponseDeadline() {
        return responseDeadline;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public UUID getSupersedesId() {
        return supersedesId;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public Long getVersion() {
        return version;
    }

    /** Changes the terms of a draft. */
    public void revise(
            String role,
            BigDecimal monthlySalary,
            String allowances,
            String terms,
            LocalDate expectedJoining,
            LocalDate offerDate,
            LocalDate responseDeadline) {
        this.role = role;
        this.monthlySalary = monthlySalary;
        this.allowances = allowances;
        this.terms = terms;
        this.expectedJoining = expectedJoining;
        this.offerDate = offerDate;
        this.responseDeadline = responseDeadline;
    }

    public void issue(UUID by, Instant at) {
        this.status = OfferStatus.ISSUED;
        this.issuedBy = by;
        this.issuedAt = at;
    }

    public void replacing(UUID supersededId) {
        this.supersedesId = supersededId;
    }

    public void supersede() {
        this.status = OfferStatus.SUPERSEDED;
    }

    public void expire() {
        this.status = OfferStatus.EXPIRED;
    }

    public void decline(String reason, UUID by, Instant at) {
        this.status = OfferStatus.DECLINED;
        this.declineReason = reason;
        this.decidedBy = by;
        this.decidedAt = at;
    }

    public void accept(UUID teacherId, UUID by, Instant at) {
        this.status = OfferStatus.ACCEPTED;
        this.teacherId = teacherId;
        this.decidedBy = by;
        this.decidedAt = at;
    }
}
