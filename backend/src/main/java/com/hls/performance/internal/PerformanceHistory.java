package com.hls.performance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of the append-only history of a performance record (a database trigger refuses updates and deletes). */
@Entity
@Table(name = "performance_history")
public class PerformanceHistory {

    @Id
    UUID id;

    @Column(name = "record_type", nullable = false)
    String recordType;

    @Column(name = "record_id", nullable = false)
    UUID recordId;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "event", nullable = false)
    String event;

    @Column(name = "actor_user_id")
    UUID actorUserId;

    @Column(name = "actor_name")
    String actorName;

    @Column(name = "at", nullable = false)
    Instant at;

    @Column(name = "before_json")
    String beforeJson;

    @Column(name = "after_json")
    String afterJson;

    @Column(name = "note")
    String note;

    protected PerformanceHistory() {
        // JPA
    }

    PerformanceHistory(String recordType, UUID recordId, UUID teacherId, String event, UUID actorUserId, String actorName, Instant at,
            String beforeJson, String afterJson, String note) {
        this.id = UUID.randomUUID();
        this.recordType = recordType;
        this.recordId = recordId;
        this.teacherId = teacherId;
        this.event = event;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.at = at;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.note = note;
    }
}
