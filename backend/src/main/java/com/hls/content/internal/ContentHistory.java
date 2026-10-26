package com.hls.content.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One append-only line of the history of a content piece (spec 029 FR-015). */
@Entity
@Table(name = "content_history")
public class ContentHistory {

    @Id
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

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

    protected ContentHistory() {}

    ContentHistory(UUID contentId, String event, UUID actorUserId, Instant at, String beforeValue, String afterValue, String note) {
        this.id = UUID.randomUUID();
        this.contentId = contentId;
        this.event = event;
        this.actorUserId = actorUserId;
        this.at = at;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.note = note;
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
