package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.FeedbackCategory;
import com.hls.performance.internal.PerformanceTypes.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What a School's Principal said about a Teacher (spec 026), kept apart from observation ratings. Versioned like {@link Observation}. */
@Entity
@Table(name = "principal_feedback")
public class PrincipalFeedback {

    @Id
    UUID id;

    @Column(name = "root_id", nullable = false)
    UUID rootId;

    @Column(name = "version_no", nullable = false)
    int versionNo;

    @Column(name = "current", nullable = false)
    boolean current;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "school_id", nullable = false)
    UUID schoolId;

    @Column(name = "principal_name", nullable = false)
    String principalName;

    @Column(name = "given_on", nullable = false)
    LocalDate givenOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    FeedbackCategory category;

    @Column(name = "comments", nullable = false)
    String comments;

    @Column(name = "action_required", nullable = false)
    boolean actionRequired;

    @Column(name = "observation_root_id")
    UUID observationRootId;

    @Column(name = "recorded_by", nullable = false)
    UUID recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    FeedbackStatus status;

    @Column(name = "withdrawn_reason")
    String withdrawnReason;

    @Column(name = "withdrawn_by")
    UUID withdrawnBy;

    @Column(name = "withdrawn_at")
    Instant withdrawnAt;

    @Column(name = "issue_id")
    UUID issueId;

    @Column(name = "correction_reason")
    String correctionReason;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected PrincipalFeedback() {
        // JPA
    }

    PrincipalFeedback(UUID teacherId, UUID schoolId, String principalName, LocalDate givenOn, FeedbackCategory category, String comments,
            boolean actionRequired, UUID observationRootId, UUID recordedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.rootId = this.id;
        this.versionNo = 1;
        this.current = true;
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.principalName = principalName;
        this.givenOn = givenOn;
        this.category = category;
        this.comments = comments;
        this.actionRequired = actionRequired;
        this.observationRootId = observationRootId;
        this.recordedBy = recordedBy;
        this.status = FeedbackStatus.RECORDED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    PrincipalFeedback nextVersion(String reason, Instant now) {
        PrincipalFeedback next = new PrincipalFeedback(
                teacherId, schoolId, principalName, givenOn, category, comments, actionRequired, observationRootId, recordedBy, now);
        next.rootId = rootId;
        next.versionNo = versionNo + 1;
        next.issueId = issueId;
        next.status = status;
        next.correctionReason = reason;
        return next;
    }
}
