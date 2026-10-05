package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A person interviewed at a campus drive. {@code phoneKey} is the normalized phone used for duplicate rules. */
@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    private UUID id;

    @Column(name = "drive_id", nullable = false)
    private UUID driveId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "phone_key", nullable = false)
    private String phoneKey;

    @Column(name = "email")
    private String email;

    @Column(name = "degree")
    private String degree;

    @Column(name = "study_year")
    private String studyYear;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private Outcome outcome;

    @Column(name = "outcome_by")
    private UUID outcomeBy;

    @Column(name = "outcome_at")
    private Instant outcomeAt;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Candidate() {}

    public Candidate(
            UUID driveId,
            String name,
            String phone,
            String phoneKey,
            String email,
            String degree,
            String studyYear,
            String notes,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.driveId = driveId;
        this.name = name;
        this.phone = phone;
        this.phoneKey = phoneKey;
        this.email = email;
        this.degree = degree;
        this.studyYear = studyYear;
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriveId() {
        return driveId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getPhoneKey() {
        return phoneKey;
    }

    public String getEmail() {
        return email;
    }

    public String getDegree() {
        return degree;
    }

    public String getStudyYear() {
        return studyYear;
    }

    public String getNotes() {
        return notes;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public UUID getOutcomeBy() {
        return outcomeBy;
    }

    public Instant getOutcomeAt() {
        return outcomeAt;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public Long getVersion() {
        return version;
    }

    public void decide(Outcome outcome, UUID by, Instant at) {
        this.outcome = outcome;
        this.outcomeBy = by;
        this.outcomeAt = at;
    }

    public void linkTeacher(UUID teacherId) {
        this.teacherId = teacherId;
    }
}
