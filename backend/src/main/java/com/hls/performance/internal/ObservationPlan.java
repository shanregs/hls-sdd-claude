package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.PlanReason;
import com.hls.performance.internal.PerformanceTypes.PlanStatus;
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

/** A planned observation of a Teacher; it is worked as an Observation task of the Teacher's Manager (spec 025). */
@Entity
@Table(name = "observation_plan")
public class ObservationPlan {

    @Id
    UUID id;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "school_id", nullable = false)
    UUID schoolId;

    @Column(name = "planned_on", nullable = false)
    LocalDate plannedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    PlanReason reason;

    @Column(name = "reason_text")
    String reasonText;

    @Column(name = "planned_by", nullable = false)
    UUID plannedBy;

    @Column(name = "source_feedback_root_id")
    UUID sourceFeedbackRootId;

    @Column(name = "issue_id")
    UUID issueId;

    @Column(name = "task_source", nullable = false)
    String taskSource;

    @Column(name = "task_source_key", nullable = false)
    String taskSourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    PlanStatus status;

    @Column(name = "completed_by_observation_root_id")
    UUID completedByObservationRootId;

    @Column(name = "cancel_reason")
    String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected ObservationPlan() {
        // JPA
    }

    ObservationPlan(UUID teacherId, UUID schoolId, LocalDate plannedOn, PlanReason reason, String reasonText, UUID plannedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.plannedOn = plannedOn;
        this.reason = reason;
        this.reasonText = reasonText;
        this.plannedBy = plannedBy;
        this.taskSource = PerformanceTypes.TASK_SOURCE;
        // the task is addressed by the plan's own id, so a finished plan never blocks the next one
        this.taskSourceKey = id.toString();
        this.status = PlanStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
