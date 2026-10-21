package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import com.hls.performance.internal.PerformanceTypes.Resolution;
import com.hls.performance.internal.PerformanceTypes.SourceType;
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

/** A Teacher's performance issue (spec 026): at most one is open per Teacher; later results are added to it. */
@Entity
@Table(name = "performance_issue")
public class PerformanceIssue {

    @Id
    UUID id;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "school_id", nullable = false)
    UUID schoolId;

    @Column(name = "manager_user_id")
    UUID managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    SourceType sourceType;

    @Column(name = "source_root_id", nullable = false)
    UUID sourceRootId;

    @Column(name = "category", nullable = false)
    String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution")
    Resolution resolution;

    @Column(name = "correction_rounds", nullable = false)
    int correctionRounds;

    @Column(name = "opened_by")
    UUID openedBy;

    @Column(name = "opened_on", nullable = false)
    LocalDate openedOn;

    @Column(name = "closed_by")
    UUID closedBy;

    @Column(name = "closed_on")
    LocalDate closedOn;

    @Column(name = "closing_reason")
    String closingReason;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected PerformanceIssue() {
        // JPA
    }

    PerformanceIssue(UUID teacherId, UUID schoolId, UUID managerUserId, SourceType sourceType, UUID sourceRootId, String category,
            UUID openedBy, LocalDate openedOn, Instant now) {
        this.id = UUID.randomUUID();
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.managerUserId = managerUserId;
        this.sourceType = sourceType;
        this.sourceRootId = sourceRootId;
        this.category = category;
        this.status = IssueStatus.OPEN;
        this.openedBy = openedBy;
        this.openedOn = openedOn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    boolean isActive() {
        return IssueStatus.ACTIVE.contains(status);
    }

    void close(IssueStatus to, Resolution how, UUID by, LocalDate on, String reason, Instant now) {
        this.status = to;
        this.resolution = how;
        this.closedBy = by;
        this.closedOn = on;
        this.closingReason = reason;
        this.updatedAt = now;
    }
}
