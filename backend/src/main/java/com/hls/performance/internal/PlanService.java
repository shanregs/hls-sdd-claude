package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.performance.api.ObservationPlanned;
import com.hls.performance.internal.PerformanceTypes.ObservationStatus;
import com.hls.performance.internal.PerformanceTypes.PlanReason;
import com.hls.performance.internal.PerformanceTypes.PlanStatus;
import com.hls.performance.internal.TeacherFacts.TeacherContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planned observations (spec 026 US2): one open plan per Teacher, worked as an Observation task of the Teacher's Manager.
 * Planning twice changes the open plan instead of making a second task or a second notification.
 */
@Service
public class PlanService {

    public static final int MAX_REASON_TEXT = 500;

    public record PlanRequest(UUID teacherId, LocalDate plannedOn, PlanReason reason, String reasonText) {}

    public record UpdateRequest(LocalDate plannedOn, PlanReason reason, String reasonText, Long version) {}

    public record Actions(boolean edit, boolean cancel) {}

    public record PlanView(
            UUID id,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            LocalDate plannedOn,
            String reason,
            String reasonLabel,
            String reasonText,
            String status,
            UUID plannedBy,
            String plannedByName,
            UUID sourceFeedbackRootId,
            UUID issueId,
            UUID managerUserId,
            String managerName,
            boolean overdue,
            long daysOverdue,
            String lastResult,
            boolean created,
            Actions actions,
            Long version) {}

    private final PlanRepository plans;
    private final ObservationRepository observations;
    private final TeacherFacts facts;
    private final ScopeGuard scope;
    private final PerformanceRecorder recorder;
    private final TaskBridge tasks;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;

    public PlanService(
            PlanRepository plans,
            ObservationRepository observations,
            TeacherFacts facts,
            ScopeGuard scope,
            PerformanceRecorder recorder,
            TaskBridge tasks,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock) {
        this.plans = plans;
        this.observations = observations;
        this.facts = facts;
        this.scope = scope;
        this.recorder = recorder;
        this.tasks = tasks;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ plan

    /** Plans an observation, or updates the Teacher's open plan (the view says which through {@code created}). */
    @Transactional
    public PlanView plan(Caller caller, PlanRequest request) {
        if (request == null || request.teacherId() == null) {
            throw new InvalidInputException("Choose the Teacher.");
        }
        return plan(caller, request.teacherId(), request.plannedOn(), request.reason(), request.reasonText(), null);
    }

    /** Plans from Principal feedback marked action required: reason School feedback, the feedback linked to the plan. */
    @Transactional
    public PlanView planFromFeedback(Caller caller, UUID teacherId, UUID feedbackRootId, LocalDate plannedOn) {
        LocalDate on = plannedOn == null ? businessDate.today() : plannedOn;
        return plan(caller, teacherId, on, PlanReason.SCHOOL_FEEDBACK, null, feedbackRootId);
    }

    private PlanView plan(Caller caller, UUID teacherId, LocalDate plannedOn, PlanReason reason, String reasonText, UUID feedbackRootId) {
        scope.requireTeacher(caller, teacherId);
        TeacherContext teacher = facts.require(teacherId);
        requirePlannable(teacher);
        if (plannedOn == null) {
            throw new InvalidInputException("Choose the date of the observation.");
        }
        if (plannedOn.isBefore(businessDate.today())) {
            throw new InvalidInputException("The date of the observation cannot be in the past.");
        }
        if (reason == null) {
            throw new InvalidInputException("Choose why the observation is planned.");
        }
        String text = cleanReasonText(reason, reasonText);
        ObservationPlan open = plans.findFirstByTeacherIdAndStatus(teacherId, PlanStatus.OPEN).orElse(null);
        if (open != null) {
            return changeOpen(caller, open, teacher, plannedOn, reason, text, feedbackRootId);
        }
        ObservationPlan plan = new ObservationPlan(teacherId, teacher.schoolId(), plannedOn, reason, text, caller.userId(), clock.instant());
        plan.sourceFeedbackRootId = feedbackRootId;
        // the task first: a Teacher with no Manager is refused before anything is stored
        tasks.create(plan, teacher, label(reason, text), caller.userId());
        plans.saveAndFlush(plan);
        recorder.event(PerformanceRecorder.PLAN, plan.id, teacherId, "PLANNED", caller.userId(),
                "Observation of " + teacher.name() + " planned for " + plannedOn + " (" + label(reason, text) + ")");
        events.publishEvent(new ObservationPlanned(plan.id, teacherId, teacher.name(), teacher.managerUserId(), plannedOn, label(reason, text)));
        return view(plan, teacher, true, caller);
    }

    private PlanView changeOpen(Caller caller, ObservationPlan open, TeacherContext teacher, LocalDate plannedOn, PlanReason reason, String text,
            UUID feedbackRootId) {
        boolean changed = !open.plannedOn.equals(plannedOn) || open.reason != reason || !java.util.Objects.equals(open.reasonText, text);
        if (changed) {
            recorder.change(PerformanceRecorder.PLAN, open.id, open.teacherId, "REPLANNED", caller.userId(), "plannedOn", open.plannedOn, plannedOn,
                    label(reason, text));
            open.plannedOn = plannedOn;
            open.reason = reason;
            open.reasonText = text;
        }
        if (feedbackRootId != null && open.sourceFeedbackRootId == null) {
            open.sourceFeedbackRootId = feedbackRootId;
            changed = true;
        }
        if (changed) {
            open.updatedAt = clock.instant();
            plans.saveAndFlush(open);
            tasks.reschedule(open, teacher, label(reason, text));
        }
        return view(open, teacher, false, caller);
    }

    /** Changes the date or reason of the open plan. */
    @Transactional
    public PlanView update(Caller caller, UUID id, UpdateRequest request) {
        ObservationPlan plan = visible(caller, id);
        StaleVersion.check(ObservationPlan.class, id, plan.version, request.version());
        if (plan.status != PlanStatus.OPEN) {
            throw new ConflictException("Only an open plan can be changed.");
        }
        TeacherContext teacher = facts.require(plan.teacherId);
        LocalDate on = request.plannedOn() == null ? plan.plannedOn : request.plannedOn();
        PlanReason reason = request.reason() == null ? plan.reason : request.reason();
        if (!on.equals(plan.plannedOn) && on.isBefore(businessDate.today())) {
            throw new InvalidInputException("The date of the observation cannot be in the past.");
        }
        String text = cleanReasonText(reason, request.reasonText() == null ? plan.reasonText : request.reasonText());
        return changeOpen(caller, plan, teacher, on, reason, text, null);
    }

    @Transactional
    public void cancel(Caller caller, UUID id, String reason) {
        ObservationPlan plan = visible(caller, id);
        if (plan.status != PlanStatus.OPEN) {
            throw new ConflictException("Only an open plan can be cancelled.");
        }
        cancelPlan(plan, required(reason, 3, MAX_REASON_TEXT, "The reason"), caller.userId());
    }

    // ------------------------------------------------------------------ for the other services

    /**
     * A corrective action needs a follow-up observation on {@code followUpOn}: the Teacher's open plan is reused (kept at the
     * earlier date) or a new one is made. Returns the plan so the corrective action can record its key.
     */
    ObservationPlan planForFollowUp(Caller caller, PerformanceIssue issue, TeacherContext teacher, LocalDate followUpOn) {
        requirePlannable(teacher);
        ObservationPlan open = plans.findFirstByTeacherIdAndStatus(issue.teacherId, PlanStatus.OPEN).orElse(null);
        if (open != null) {
            open.issueId = issue.id;
            if (open.plannedOn.isAfter(followUpOn)) {
                recorder.change(PerformanceRecorder.PLAN, open.id, open.teacherId, "REPLANNED", caller.userId(), "plannedOn", open.plannedOn, followUpOn,
                        "Follow-up of a corrective action");
                open.plannedOn = followUpOn;
            }
            open.updatedAt = clock.instant();
            plans.saveAndFlush(open);
            tasks.reschedule(open, teacher, label(open.reason, open.reasonText));
            return open;
        }
        ObservationPlan plan = new ObservationPlan(issue.teacherId, teacher.schoolId(), followUpOn, PlanReason.FOLLOW_UP, null, caller.userId(), clock.instant());
        plan.issueId = issue.id;
        tasks.create(plan, teacher, label(PlanReason.FOLLOW_UP, null), caller.userId());
        plans.saveAndFlush(plan);
        recorder.event(PerformanceRecorder.PLAN, plan.id, plan.teacherId, "PLANNED", caller.userId(),
                "Follow-up observation of " + teacher.name() + " planned for " + followUpOn);
        events.publishEvent(new ObservationPlanned(plan.id, plan.teacherId, teacher.name(), teacher.managerUserId(), followUpOn, label(PlanReason.FOLLOW_UP, null)));
        return plan;
    }

    /**
     * An observation was submitted: when the observer is the Teacher's Manager, the Teacher's open Observation task is
     * completed with the observation as its note and the plan is done. Any other observer leaves the task to its assignee.
     */
    void completeFor(UUID teacherId, UUID observationRootId, UUID observerUserId, LocalDate observedOn, String resultLabel) {
        ObservationPlan open = plans.findFirstByTeacherIdAndStatus(teacherId, PlanStatus.OPEN).orElse(null);
        if (open == null) {
            return;
        }
        TeacherContext teacher = facts.find(teacherId).orElse(null);
        if (teacher == null || teacher.managerUserId() == null || !teacher.managerUserId().equals(observerUserId)) {
            return;
        }
        String note = "Observation submitted: " + resultLabel + " on " + observedOn + ".";
        tasks.complete(open, observerUserId, note);
        open.status = PlanStatus.DONE;
        open.completedByObservationRootId = observationRootId;
        open.updatedAt = clock.instant();
        plans.saveAndFlush(open);
        recorder.event(PerformanceRecorder.PLAN, open.id, teacherId, "DONE", observerUserId, note);
    }

    /** Cancels the Teacher's open plan, for example when the Teacher exits or the issue is cancelled. */
    void cancelOpenFor(UUID teacherId, UUID onlyForIssue, String reason, UUID actor) {
        plans.findFirstByTeacherIdAndStatus(teacherId, PlanStatus.OPEN).ifPresent(plan -> {
            if (onlyForIssue == null || onlyForIssue.equals(plan.issueId)) {
                cancelPlan(plan, reason, actor);
            }
        });
    }

    /**
     * Gives every open plan's task to the Teacher's current Manager (there is no event when a Manager or placement changes,
     * so this runs daily and whenever Admin or Director open the Planned list). Reassigning to the same Manager changes
     * nothing. Returns how many plans followed their Teacher to another School.
     */
    @Transactional
    public int reconcile() {
        int moved = 0;
        for (ObservationPlan plan : plans.findByStatus(PlanStatus.OPEN)) {
            TeacherContext teacher = facts.find(plan.teacherId).orElse(null);
            if (teacher == null || teacher.managerUserId() == null || !teacher.active()) {
                continue;
            }
            tasks.reassign(plan, teacher.managerUserId());
            if (teacher.schoolId() != null && !teacher.schoolId().equals(plan.schoolId)) {
                recorder.change(PerformanceRecorder.PLAN, plan.id, plan.teacherId, "REASSIGNED", null, "schoolId", plan.schoolId, teacher.schoolId(),
                        "Task given to " + teacher.managerName());
                plan.schoolId = teacher.schoolId();
                plan.updatedAt = clock.instant();
                plans.save(plan);
                moved++;
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PlanView get(Caller caller, UUID id) {
        ObservationPlan plan = visible(caller, id);
        return view(plan, facts.find(plan.teacherId).orElse(null), false, caller);
    }

    PlanView view(ObservationPlan plan, TeacherContext teacher, boolean created, Caller caller) {
        LocalDate today = businessDate.today();
        boolean open = plan.status == PlanStatus.OPEN;
        boolean overdue = open && plan.plannedOn.isBefore(today);
        String lastResult = observations.findByTeacherIdAndCurrentTrueOrderByObservedOnDescCreatedAtDesc(plan.teacherId).stream()
                .filter(o -> o.status == ObservationStatus.SUBMITTED && o.overallResult != null)
                .findFirst()
                .map(o -> o.overallResult.name())
                .orElse(null);
        return new PlanView(
                plan.id,
                plan.teacherId,
                teacher == null ? null : teacher.name(),
                plan.schoolId,
                teacher == null ? null : teacher.schoolName(),
                plan.plannedOn,
                plan.reason.name(),
                plan.reason.label(),
                plan.reasonText,
                plan.status.name(),
                plan.plannedBy,
                facts.userName(plan.plannedBy),
                plan.sourceFeedbackRootId,
                plan.issueId,
                teacher == null ? null : teacher.managerUserId(),
                teacher == null ? null : teacher.managerName(),
                overdue,
                overdue ? ChronoUnit.DAYS.between(plan.plannedOn, today) : 0,
                lastResult,
                created,
                new Actions(open, open),
                plan.version);
    }

    ObservationPlan visible(Caller caller, UUID id) {
        ObservationPlan plan = plans.findById(id).orElseThrow(() -> new NotFoundException("Plan not found."));
        scope.requireRecord(caller, plan.teacherId, plan.plannedBy, "Plan");
        return plan;
    }

    List<ObservationPlan> openPlans() {
        return plans.findByStatus(PlanStatus.OPEN);
    }

    // ------------------------------------------------------------------ helpers

    private void cancelPlan(ObservationPlan plan, String reason, UUID actor) {
        tasks.cancel(plan, reason.length() < 3 ? "Plan cancelled" : reason);
        recorder.change(PerformanceRecorder.PLAN, plan.id, plan.teacherId, "CANCELLED", actor, "status", plan.status, PlanStatus.CANCELLED, reason);
        plan.status = PlanStatus.CANCELLED;
        plan.cancelReason = reason;
        plan.updatedAt = clock.instant();
        plans.saveAndFlush(plan);
    }

    private static void requirePlannable(TeacherContext teacher) {
        if (!teacher.active()) {
            throw new InvalidInputException("An observation can be planned only for an active Teacher. " + teacher.name() + " is "
                    + teacher.status().toLowerCase().replace('_', ' ') + ".");
        }
        if (!teacher.placed()) {
            throw new InvalidInputException(teacher.name() + " is not placed at a School, so there is nothing to observe.");
        }
    }

    private static String cleanReasonText(PlanReason reason, String raw) {
        String text = raw == null || raw.isBlank() ? null : raw.trim();
        if (reason == PlanReason.OTHER && text == null) {
            throw new InvalidInputException("Say why the observation is planned.");
        }
        if (text != null && text.length() > MAX_REASON_TEXT) {
            throw new InvalidInputException("The reason must be at most " + MAX_REASON_TEXT + " characters.");
        }
        return text;
    }

    private static String label(PlanReason reason, String text) {
        return reason == PlanReason.OTHER ? "Other: " + text : reason.label();
    }

    private static String required(String raw, int min, int max, String what) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < min) {
            throw new InvalidInputException(what + " is required (at least " + min + " characters).");
        }
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }
}
