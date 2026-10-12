package com.hls.tasks.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of a task's append-only history; the database refuses to update or delete it. */
@Entity
@Table(name = "task_history")
public class TaskHistory {

    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event", nullable = false)
    private String event;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "note")
    private String note;

    protected TaskHistory() {
        // JPA
    }

    TaskHistory(UUID taskId, String event, UUID actorUserId, Instant at, String beforeValue, String afterValue, String note) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.event = event;
        this.actorUserId = actorUserId;
        this.at = at;
        this.beforeValue = cut(beforeValue, 600);
        this.afterValue = cut(afterValue, 600);
        this.note = cut(note, 2000);
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getEvent() {
        return event;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getAt() {
        return at;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public String getNote() {
        return note;
    }
}
