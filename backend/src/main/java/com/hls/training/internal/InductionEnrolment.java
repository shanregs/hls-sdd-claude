package com.hls.training.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recruit's place in a batch; the batch dates are copied so the database can refuse overlapping batches. */
@Entity
@Table(name = "induction_enrolment")
public class InductionEnrolment {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "enrolled_by", nullable = false)
    private UUID enrolledBy;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    /** COMPLETED or NOT_COMPLETED once signed off. */
    @Column(name = "result")
    private String result;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "signed_by")
    private UUID signedBy;

    @Column(name = "signed_at")
    private Instant signedAt;

    /** NEXT_BATCH or RELEASED after a NOT_COMPLETED result. */
    @Column(name = "follow_up")
    private String followUp;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected InductionEnrolment() {}

    public InductionEnrolment(UUID batchId, UUID teacherId, LocalDate startsOn, LocalDate endsOn, UUID by, Instant now) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.teacherId = teacherId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.enrolledBy = by;
        this.enrolledAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getResult() {
        return result;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getSignedBy() {
        return signedBy;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getFollowUp() {
        return followUp;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isSignedOff() {
        return result != null;
    }

    public void signOff(String result, String remarks, UUID by, Instant at) {
        this.result = result;
        this.remarks = remarks;
        this.signedBy = by;
        this.signedAt = at;
    }

    public void followUp(String action) {
        this.followUp = action;
    }
}
