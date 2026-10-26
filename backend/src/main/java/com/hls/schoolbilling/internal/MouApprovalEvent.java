package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry of a MoU approval and document history (spec 031). Append-only (database trigger). */
@Entity
@Table(name = "mou_approval_event")
public class MouApprovalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Insertion order, so entries written in the same instant keep their order. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "round", nullable = false, updatable = false)
    private int round;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "from_status", updatable = false)
    private String fromStatus;

    @Column(name = "to_status", updatable = false)
    private String toStatus;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_role", updatable = false)
    private String actorRole;

    @Column(name = "document_version", updatable = false)
    private Integer documentVersion;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    protected MouApprovalEvent() {
        // JPA
    }

    public MouApprovalEvent(
            UUID contractId,
            int round,
            String action,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorRole,
            Integer documentVersion,
            String note,
            String detail,
            Instant at) {
        this.contractId = contractId;
        this.round = round;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.documentVersion = documentVersion;
        this.note = note;
        this.detail = detail;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public int getRound() {
        return round;
    }

    public String getAction() {
        return action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public String getNote() {
        return note;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getAt() {
        return at;
    }
}
