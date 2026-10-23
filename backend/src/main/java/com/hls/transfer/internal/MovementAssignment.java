package com.hls.transfer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Links an approved request to the placement rows it ended or created (plain ids, no foreign key). */
@Entity
@Table(name = "movement_assignment")
public class MovementAssignment {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "effect", nullable = false)
    private String effect;

    protected MovementAssignment() {
        // JPA
    }

    MovementAssignment(UUID requestId, UUID assignmentId, UUID teacherId, String effect) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.assignmentId = assignmentId;
        this.teacherId = teacherId;
        this.effect = effect;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getAssignmentId() { return assignmentId; }
    public UUID getTeacherId() { return teacherId; }
    public String getEffect() { return effect; }
}
