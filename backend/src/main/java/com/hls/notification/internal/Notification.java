package com.hls.notification.internal;

import com.hls.notification.api.NotificationChannel;
import com.hls.notification.api.NotificationType;
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

/** One in-app notification for one recipient user (spec 010). */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "link")
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(name = "group_key", updatable = false)
    private String groupKey;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(
            UUID recipientUserId,
            NotificationType type,
            String title,
            String message,
            String link,
            String groupKey,
            String detail,
            Instant now) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.groupKey = groupKey;
        this.detail = detail;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Folds another change into this unread notification: new text and day list, activity time moves on. */
    public void merge(String newTitle, String newMessage, String newDetail, Instant now) {
        this.title = newTitle;
        this.message = newMessage;
        this.detail = newDetail;
        this.updatedAt = now;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
