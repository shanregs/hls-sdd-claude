package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.Area;
import com.hls.performance.internal.PerformanceTypes.NotHeldReason;
import com.hls.performance.internal.PerformanceTypes.ObservationStatus;
import com.hls.performance.internal.PerformanceTypes.PermissionWay;
import com.hls.performance.internal.PerformanceTypes.Result;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * One version of a classroom observation (spec 026). A correction is a new row with the same {@code rootId}; the earlier
 * row is kept as SUPERSEDED. Fields are package-private: the services in this package change them and record the history.
 * An area that was not observed has no rating row.
 */
@Entity
@Table(name = "observation")
public class Observation {

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

    @Column(name = "observer_user_id", nullable = false)
    UUID observerUserId;

    @Column(name = "observer_name", nullable = false)
    String observerName;

    @Column(name = "manager_user_id")
    UUID managerUserId;

    @Column(name = "observed_on", nullable = false)
    LocalDate observedOn;

    @Column(name = "class_name")
    String className;

    @Column(name = "subject")
    String subject;

    @Column(name = "duration_minutes")
    Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ObservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "not_held_reason")
    NotHeldReason notHeldReason;

    @Column(name = "not_held_text")
    String notHeldText;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_result")
    Result overallResult;

    @Column(name = "average_rating")
    BigDecimal averageRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_result")
    Result suggestedResult;

    @Column(name = "comments")
    String comments;

    @Column(name = "strengths")
    String strengths;

    @Column(name = "improvements")
    String improvements;

    @Column(name = "principal_comments")
    String principalComments;

    @Column(name = "permission_by")
    String permissionBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_way")
    PermissionWay permissionWay;

    @Column(name = "permission_on")
    LocalDate permissionOn;

    @Column(name = "issue_id")
    UUID issueId;

    @Column(name = "followup_of_issue_id")
    UUID followupOfIssueId;

    @Column(name = "correction_reason")
    String correctionReason;

    @Column(name = "submitted_at")
    Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "observation_rating", joinColumns = @JoinColumn(name = "observation_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "area")
    @Column(name = "rating")
    Map<Area, Integer> ratings = new EnumMap<>(Area.class);

    protected Observation() {
        // JPA
    }

    Observation(UUID teacherId, UUID schoolId, UUID observerUserId, String observerName, UUID managerUserId, LocalDate observedOn, Instant now) {
        this.id = UUID.randomUUID();
        this.rootId = this.id;
        this.versionNo = 1;
        this.current = true;
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.observerUserId = observerUserId;
        this.observerName = observerName;
        this.managerUserId = managerUserId;
        this.observedOn = observedOn;
        this.status = ObservationStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new version for a correction: every value copied, the version number one higher, the earlier row left to the caller. */
    Observation nextVersion(String reason, Instant now) {
        Observation next = new Observation(teacherId, schoolId, observerUserId, observerName, managerUserId, observedOn, now);
        next.rootId = rootId;
        next.versionNo = versionNo + 1;
        next.status = status;
        next.className = className;
        next.subject = subject;
        next.durationMinutes = durationMinutes;
        next.notHeldReason = notHeldReason;
        next.notHeldText = notHeldText;
        next.overallResult = overallResult;
        next.averageRating = averageRating;
        next.suggestedResult = suggestedResult;
        next.comments = comments;
        next.strengths = strengths;
        next.improvements = improvements;
        next.principalComments = principalComments;
        next.permissionBy = permissionBy;
        next.permissionWay = permissionWay;
        next.permissionOn = permissionOn;
        next.issueId = issueId;
        next.followupOfIssueId = followupOfIssueId;
        next.submittedAt = submittedAt;
        next.correctionReason = reason;
        next.ratings = new EnumMap<>(Area.class);
        next.ratings.putAll(ratings);
        return next;
    }
}
