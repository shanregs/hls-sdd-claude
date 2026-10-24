package com.hls.transfer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A Teacher taking part in a request; at most one open row per Teacher (partial unique index). */
@Entity
@Table(name = "movement_participant")
public class MovementParticipant {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "open", nullable = false)
    private boolean open;

    protected MovementParticipant() {
        // JPA
    }

    MovementParticipant(UUID requestId, UUID teacherId, String role) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.teacherId = teacherId;
        this.role = role;
        this.open = true;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getTeacherId() { return teacherId; }
    public String getRole() { return role; }
    public boolean isOpen() { return open; }

    void close() { this.open = false; }
}
