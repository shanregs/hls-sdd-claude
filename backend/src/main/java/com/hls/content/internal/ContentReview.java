package com.hls.content.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A reviewer decision on one version: APPROVED, or RETURNED with a comment (spec 029 FR-008). */
@Entity
@Table(name = "content_review")
public class ContentReview {

    @Id
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ContentReview() {}

    ContentReview(UUID contentId, int versionNumber, UUID reviewerUserId, String decision, String comment, Instant decidedAt) {
        this.id = UUID.randomUUID();
        this.contentId = contentId;
        this.versionNumber = versionNumber;
        this.reviewerUserId = reviewerUserId;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public String getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
