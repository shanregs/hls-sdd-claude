package com.hls.substitution.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One append-only row of a requirement's history. The table refuses UPDATE and DELETE. */
@Entity
@Table(name = "substitution_history")
public class SubstitutionHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requirement_id", nullable = false, updatable = false)
    private UUID requirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private HistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private RequirementStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private RequirementStatus toStatus;

    @Column(name = "substitute_teacher_id", updatable = false)
    private UUID substituteTeacherId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    protected SubstitutionHistoryEntry() {
        // JPA
    }

    public SubstitutionHistoryEntry(
            UUID requirementId,
            HistoryAction action,
            RequirementStatus fromStatus,
            RequirementStatus toStatus,
            UUID substituteTeacherId,
            UUID actorUserId,
            String note,
            Instant at) {
        this.requirementId = requirementId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.substituteTeacherId = substituteTeacherId;
        this.actorUserId = actorUserId;
        this.note = note;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public HistoryAction getAction() {
        return action;
    }

    public RequirementStatus getFromStatus() {
        return fromStatus;
    }

    public RequirementStatus getToStatus() {
        return toStatus;
    }

    public UUID getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getNote() {
        return note;
    }

    public Instant getAt() {
        return at;
    }
}
