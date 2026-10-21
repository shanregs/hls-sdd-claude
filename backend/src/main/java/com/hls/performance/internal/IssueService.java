package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.performance.api.ReferralRecorded;
import com.hls.performance.internal.PerformanceTypes.FeedbackStatus;
import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import com.hls.performance.internal.PerformanceTypes.Outcome;
import com.hls.performance.internal.PerformanceTypes.PlanStatus;
import com.hls.performance.internal.PerformanceTypes.Recommendation;
import com.hls.performance.internal.PerformanceTypes.Resolution;
import com.hls.performance.internal.PerformanceTypes.Result;
import com.hls.performance.internal.PerformanceTypes.SourceType;
import com.hls.performance.internal.TeacherFacts.TeacherContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performance issues (spec 026 US4 and US5): opened from a Needs improvement or Unsuitable observation or from Principal
 * feedback, worked through corrective actions and follow-up observations (no limit on rounds), and ended by an improvement,
 * a referral for transfer or replacement, a Director's decision, or the Teacher leaving.
 */
@Service
public class IssueService {

    public static final int MIN_FOLLOW_UP_DAYS = 1;
    public static final int MAX_FOLLOW_UP_DAYS = 60;
    public static final int MIN_REFERRAL_REASON = 20;

    public record CorrectiveRequest(String discussed, String requiredChange, LocalDate followUpOn, Long version) {}

    public record ReferralRequest(Recommendation recommendation, String reason, Long version) {}

    /** {@code outcome} is CANCELLED or CONTINUE. */
    public record CloseRequest(String outcome, String reason, Long version) {}

    public record LinkView(String type, UUID rootId, UUID id, LocalDate date, String result, String summary) {}

    public record CorrectiveActionView(
            int round, String discussed, String requiredChange, LocalDate followUpOn, UUID recordedBy, String recordedByName, LocalDate recordedOn,
            UUID followUpObservationRootId) {}

    public record ReferralView(
            String recommendation, String reason, UUID referredBy, String referredByName, LocalDate referredOn, String decisionOutcome,
            LocalDate decisionReportedOn) {}

    public record Actions(boolean correctiveAction, boolean refer, boolean cancel, boolean closeContinue, boolean escalate) {}

    public record IssueView(
            UUID id,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            UUID managerUserId,
            String managerName,
            String sourceType,
            UUID sourceRootId,
            String category,
            String status,
            String resolution,
            int correctionRounds,
            UUID openedBy,
            String openedByName,
            LocalDate openedOn,
            LocalDate closedOn,
            String closingReason,
            boolean needsCorrectiveAction,
            LocalDate followUpOn,
            List<LinkView> links,
            List<CorrectiveActionView> correctiveActions,
            ReferralView referral,
            Actions actions,
            Long version) {}

    private final IssueRepository issues;
    private final IssueLinkRepository links;
    private final CorrectiveActionRepository actions;
    private final ReferralRepository referrals;
    private final ObservationRepository observations;
    private final FeedbackRepository feedback;
    private final PlanRepository plans;
    private final PlanService planService;
    private final TeacherFacts facts;
    private final ScopeGuard scope;
    private final PerformanceRecorder recorder;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;

    public IssueService(
            IssueRepository issues,
            IssueLinkRepository links,
            CorrectiveActionRepository actions,
            ReferralRepository referrals,
            ObservationRepository observations,
            FeedbackRepository feedback,
            PlanRepository plans,
            PlanService planService,
            TeacherFacts facts,
            ScopeGuard scope,
            PerformanceRecorder recorder,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock) {
        this.issues = issues;
        this.links = links;
        this.actions = actions;
        this.referrals = referrals;
        this.observations = observations;
        this.feedback = feedback;
        this.plans = plans;
        this.planService = planService;
        this.facts = facts;
        this.scope = scope;
        this.recorder = recorder;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ opening and joining (called by the other services)

    /** The Teacher's open issue, if any. */
    Optional<PerformanceIssue> activeOf(UUID teacherId) {
        return issues.findFirstByTeacherIdAndStatusIn(teacherId, IssueStatus.ACTIVE);
    }

    /** The Teacher's issue that is waiting for a follow-up observation, if any. */
    Optional<PerformanceIssue> underCorrectionOf(UUID teacherId) {
        return issues.findFirstByTeacherIdAndStatusIn(teacherId, List.of(IssueStatus.UNDER_CORRECTION));
    }

    /** Opens an issue for the Teacher, or adds the record to the one already open (FR-019). */
    PerformanceIssue openOrJoin(UUID actor, UUID teacherId, UUID schoolId, UUID managerUserId, SourceType type, UUID rootId, String category) {
        LocalDate today = businessDate.today();
        PerformanceIssue issue = activeOf(teacherId).orElse(null);
        if (issue == null) {
            issue = new PerformanceIssue(teacherId, schoolId, managerUserId, type, rootId, category, actor, today, clock.instant());
            issues.saveAndFlush(issue);
            recorder.event(PerformanceRecorder.ISSUE, issue.id, teacherId, "ISSUE_OPENED", actor, "Opened from " + type.name().toLowerCase() + ": " + category);
        }
        link(issue, type, rootId);
        return issue;
    }

    private void link(PerformanceIssue issue, SourceType type, UUID rootId) {
        if (!links.existsByIssueIdAndRecordTypeAndRecordRootId(issue.id, type, rootId)) {
            links.save(new IssueLink(issue.id, type, rootId, businessDate.today()));
        }
    }

    /**
     * An observation was submitted. A follow-up of an issue under correction resolves it when Good and otherwise asks for the
     * next round; any other Needs improvement or Unsuitable result opens or joins an issue. Returns the issue it belongs to.
     */
    PerformanceIssue afterObservationSubmitted(Observation observation, Caller caller) {
        Result result = observation.overallResult;
        PerformanceIssue followUp = observation.followupOfIssueId == null
                ? null
                : issues.findById(observation.followupOfIssueId).filter(i -> i.status == IssueStatus.UNDER_CORRECTION).orElse(null);
        if (followUp != null) {
            link(followUp, SourceType.OBSERVATION, observation.rootId);
            actions.findFirstByIssueIdOrderByRoundNoDesc(followUp.id).ifPresent(a -> {
                if (a.followUpObservationRootId == null) {
                    a.followUpObservationRootId = observation.rootId;
                    actions.save(a);
                }
            });
            recorder.event(PerformanceRecorder.ISSUE, followUp.id, followUp.teacherId, "FOLLOW_UP_DONE", caller.userId(),
                    "Follow-up observation: " + result.label());
            if (result == Result.GOOD) {
                finish(followUp, IssueStatus.RESOLVED, Resolution.IMPROVED, caller.userId(), "Improved at the follow-up observation");
            }
            return followUp;
        }
        if (result.needsIssue()) {
            return openOrJoin(caller.userId(), observation.teacherId, observation.schoolId, observation.managerUserId, SourceType.OBSERVATION,
                    observation.rootId, result.name());
        }
        return null;
    }

    /** Principal feedback with action required: open an issue for it, or add it to the Teacher's open one. */
    @Transactional
    public IssueView openFromFeedback(Caller caller, UUID teacherId, UUID feedbackRootId) {
        scope.requireTeacher(caller, teacherId);
        PrincipalFeedback record = feedback.findByRootIdAndCurrentTrue(feedbackRootId)
                .filter(f -> f.teacherId.equals(teacherId))
                .orElseThrow(() -> new NotFoundException("Feedback not found."));
        if (record.status != FeedbackStatus.RECORDED) {
            throw new ConflictException("Withdrawn feedback cannot open an issue.");
        }
        TeacherContext teacher = facts.require(teacherId);
        PerformanceIssue issue = openOrJoin(caller.userId(), teacherId, record.schoolId, teacher.managerUserId(), SourceType.FEEDBACK, record.rootId,
                record.category.name());
        if (record.issueId == null || !record.issueId.equals(issue.id)) {
            record.issueId = issue.id;
            record.updatedAt = clock.instant();
            feedback.save(record);
        }
        return view(issue, caller);
    }

    // ------------------------------------------------------------------ the loop

    /** A corrective action moves the issue to Under correction and plans the follow-up observation (FR-020). */
    @Transactional
    public IssueView recordCorrectiveAction(Caller caller, UUID id, CorrectiveRequest request) {
        PerformanceIssue issue = visible(caller, id);
        StaleVersion.check(PerformanceIssue.class, id, issue.version, request.version());
        if (issue.status != IssueStatus.OPEN && issue.status != IssueStatus.UNDER_CORRECTION) {
            throw new ConflictException("A corrective action can be recorded only for an open issue. This one is "
                    + issue.status.name().toLowerCase().replace('_', ' ') + ".");
        }
        String discussed = required(request.discussed(), 5, PerformanceTypes.MAX_TEXT, "What was discussed with the Teacher");
        String change = required(request.requiredChange(), 5, PerformanceTypes.MAX_TEXT, "What must change");
        LocalDate today = businessDate.today();
        LocalDate on = request.followUpOn();
        if (on == null) {
            throw new InvalidInputException("Choose the follow-up date.");
        }
        long days = ChronoUnit.DAYS.between(today, on);
        if (days < MIN_FOLLOW_UP_DAYS || days > MAX_FOLLOW_UP_DAYS) {
            throw new InvalidInputException("The follow-up date must be between " + MIN_FOLLOW_UP_DAYS + " and " + MAX_FOLLOW_UP_DAYS + " days from today.");
        }
        TeacherContext teacher = facts.require(issue.teacherId);
        int round = issue.correctionRounds + 1;
        // the task first: a Teacher with no Manager is refused before anything is stored
        ObservationPlan plan = planService.planForFollowUp(caller, issue, teacher, on);
        CorrectiveAction action = new CorrectiveAction(issue.id, round, discussed, change, on, caller.userId(), today, plan.taskSourceKey);
        actions.save(action);
        IssueStatus before = issue.status;
        issue.correctionRounds = round;
        issue.status = IssueStatus.UNDER_CORRECTION;
        issue.updatedAt = clock.instant();
        issues.saveAndFlush(issue);
        recorder.change(PerformanceRecorder.ISSUE, issue.id, issue.teacherId, "CORRECTIVE_ACTION", caller.userId(), "status", before, issue.status,
                "Round " + round + ", follow-up on " + on);
        return view(issue, caller);
    }

    /** The observer tells the Director the issue needs attention; recorded in the history only (no status change). */
    @Transactional
    public IssueView escalate(Caller caller, UUID id, String note) {
        PerformanceIssue issue = visible(caller, id);
        if (!issue.isActive()) {
            throw new ConflictException("Only an open issue can be escalated.");
        }
        String text = required(note, 5, 500, "The note");
        recorder.event(PerformanceRecorder.ISSUE, issue.id, issue.teacherId, "ESCALATED", caller.userId(), text);
        return view(issue, caller);
    }

    /** Records the recommendation to transfer or replace; the issue becomes Referred and the Directors are told (FR-022). */
    @Transactional
    public IssueView refer(Caller caller, UUID id, ReferralRequest request) {
        PerformanceIssue issue = visible(caller, id);
        StaleVersion.check(PerformanceIssue.class, id, issue.version, request.version());
        if (issue.status != IssueStatus.OPEN && issue.status != IssueStatus.UNDER_CORRECTION) {
            throw new ConflictException("Only an open issue can be referred. This one is " + issue.status.name().toLowerCase().replace('_', ' ') + ".");
        }
        if (request.recommendation() == null) {
            throw new InvalidInputException("Choose Transfer or Replace.");
        }
        String reason = required(request.reason(), MIN_REFERRAL_REASON, PerformanceTypes.MAX_TEXT, "The reason");
        referrals.save(new Referral(issue.id, request.recommendation(), reason, caller.userId(), businessDate.today()));
        IssueStatus before = issue.status;
        issue.status = IssueStatus.REFERRED;
        issue.updatedAt = clock.instant();
        issues.saveAndFlush(issue);
        recorder.change(PerformanceRecorder.ISSUE, issue.id, issue.teacherId, "REFERRED", caller.userId(), "status", before, issue.status,
                request.recommendation() + ": " + reason);
        // a pending follow-up observation no longer makes sense
        planService.cancelOpenFor(issue.teacherId, issue.id, "The Teacher was referred", caller.userId());
        events.publishEvent(new ReferralRecorded(issue.id, issue.teacherId, facts.teacherNames(List.of(issue.teacherId)).get(issue.teacherId),
                request.recommendation().name()));
        return view(issue, caller);
    }

    /** Admin or Director: cancel an issue with a reason, or close a Referred one as Resolved - continue (FR-024). */
    @Transactional
    public IssueView close(Caller caller, UUID id, CloseRequest request) {
        if (!caller.orgWide()) {
            throw new ForbiddenFieldException("Only Admin or Director can close an issue.");
        }
        PerformanceIssue issue = visible(caller, id);
        StaleVersion.check(PerformanceIssue.class, id, issue.version, request.version());
        if (!issue.isActive()) {
            throw new ConflictException("This issue is already " + issue.status.name().toLowerCase().replace('_', ' ') + ".");
        }
        String reason = required(request.reason(), 3, 500, "The reason");
        String outcome = request.outcome() == null ? "" : request.outcome().trim().toUpperCase();
        switch (outcome) {
            case "CANCELLED" -> finish(issue, IssueStatus.CANCELLED, null, caller.userId(), reason);
            case "CONTINUE" -> {
                if (issue.status != IssueStatus.REFERRED) {
                    throw new ConflictException("Only a referred issue can be closed as Resolved - continue.");
                }
                finish(issue, IssueStatus.RESOLVED, Resolution.CONTINUE, caller.userId(), reason);
            }
            default -> throw new InvalidInputException("Choose CANCELLED or CONTINUE.");
        }
        return view(issue, caller);
    }

    /** Spec 027 reports the decision on a referral (FR-023). */
    @Transactional
    public void reportDecision(UUID issueId, Outcome outcome, UUID actor) {
        PerformanceIssue issue = issues.findById(issueId).orElseThrow(() -> new NotFoundException("Issue not found."));
        if (issue.status != IssueStatus.REFERRED) {
            throw new ConflictException("Only a referred issue can take a decision.");
        }
        Referral referral = referrals.findByIssueId(issueId).orElseThrow(() -> new NotFoundException("Referral not found."));
        referral.decisionOutcome = outcome;
        referral.decisionReportedOn = businessDate.today();
        referrals.save(referral);
        recorder.event(PerformanceRecorder.ISSUE, issue.id, issue.teacherId, "DECISION_REPORTED", actor, outcome.name());
        finish(issue, IssueStatus.RESOLVED, outcome == Outcome.TRANSFERRED ? Resolution.TRANSFERRED : Resolution.REPLACED, actor,
                "Decision reported: " + outcome.name().toLowerCase());
    }

    /** The Teacher exited: every active issue closes as Resolved - exited and any open plan is cancelled (FR-024). */
    @Transactional
    public int closeForExited(UUID teacherId) {
        int closed = 0;
        for (PerformanceIssue issue : issues.findByTeacherIdOrderByOpenedOnDescCreatedAtDesc(teacherId)) {
            if (issue.isActive()) {
                finish(issue, IssueStatus.RESOLVED, Resolution.EXITED, null, "The Teacher exited");
                closed++;
            }
        }
        planService.cancelOpenFor(teacherId, null, "The Teacher exited", null);
        return closed;
    }

    /** Ids of Teachers who have an issue or plan still open, for the daily check. */
    List<UUID> teachersWithOpenWork() {
        List<UUID> ids = new ArrayList<>();
        issues.findByStatusIn(IssueStatus.ACTIVE).forEach(i -> ids.add(i.teacherId));
        plans.findByStatus(PlanStatus.OPEN).forEach(p -> ids.add(p.teacherId));
        return ids.stream().distinct().toList();
    }

    private void finish(PerformanceIssue issue, IssueStatus to, Resolution how, UUID actor, String reason) {
        IssueStatus before = issue.status;
        issue.close(to, how, actor, businessDate.today(), reason, clock.instant());
        issues.saveAndFlush(issue);
        recorder.change(PerformanceRecorder.ISSUE, issue.id, issue.teacherId, to == IssueStatus.CANCELLED ? "CANCELLED" : "RESOLVED", actor, "status",
                before, to, (how == null ? "" : how + ": ") + reason);
        if (to == IssueStatus.CANCELLED) {
            planService.cancelOpenFor(issue.teacherId, issue.id, "The issue was cancelled", actor);
        }
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public IssueView get(Caller caller, UUID id) {
        return view(visible(caller, id), caller);
    }

    PerformanceIssue visible(Caller caller, UUID id) {
        PerformanceIssue issue = issues.findById(id).orElseThrow(() -> new NotFoundException("Issue not found."));
        scope.requireRecord(caller, issue.teacherId, issue.openedBy, "Issue");
        return issue;
    }

    IssueView view(PerformanceIssue issue, Caller caller) {
        TeacherContext teacher = facts.find(issue.teacherId).orElse(null);
        List<CorrectiveAction> rounds = actions.findByIssueIdOrderByRoundNo(issue.id);
        CorrectiveAction last = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        boolean needs = issue.status == IssueStatus.UNDER_CORRECTION && last != null && last.followUpObservationRootId != null;
        List<LinkView> linkViews = new ArrayList<>();
        for (IssueLink l : links.findByIssueIdOrderByLinkedOn(issue.id)) {
            linkViews.add(linkView(l));
        }
        Referral referral = referrals.findByIssueId(issue.id).orElse(null);
        boolean open = issue.status == IssueStatus.OPEN || issue.status == IssueStatus.UNDER_CORRECTION;
        boolean director = caller.orgWide();
        return new IssueView(
                issue.id,
                issue.teacherId,
                teacher == null ? null : teacher.name(),
                issue.schoolId,
                facts.schoolNames(List.of(issue.schoolId)).get(issue.schoolId),
                teacher == null ? issue.managerUserId : teacher.managerUserId(),
                teacher == null ? null : teacher.managerName(),
                issue.sourceType.name(),
                issue.sourceRootId,
                issue.category,
                issue.status.name(),
                issue.resolution == null ? null : issue.resolution.name(),
                issue.correctionRounds,
                issue.openedBy,
                issue.openedBy == null ? "System" : facts.userName(issue.openedBy),
                issue.openedOn,
                issue.closedOn,
                issue.closingReason,
                needs,
                last == null || !open ? null : last.followUpOn,
                linkViews,
                rounds.stream()
                        .map(a -> new CorrectiveActionView(a.roundNo, a.discussed, a.requiredChange, a.followUpOn, a.recordedBy,
                                facts.userName(a.recordedBy), a.recordedOn, a.followUpObservationRootId))
                        .toList(),
                referral == null
                        ? null
                        : new ReferralView(referral.recommendation.name(), referral.reason, referral.referredBy, facts.userName(referral.referredBy),
                                referral.referredOn, referral.decisionOutcome == null ? null : referral.decisionOutcome.name(), referral.decisionReportedOn),
                new Actions(open, open, director && issue.isActive(), director && issue.status == IssueStatus.REFERRED, issue.isActive()),
                issue.version);
    }

    private LinkView linkView(IssueLink l) {
        if (l.recordType == SourceType.OBSERVATION) {
            return observations.findByRootIdAndCurrentTrue(l.recordRootId)
                    .map(o -> new LinkView("OBSERVATION", o.rootId, o.id, o.observedOn, o.overallResult == null ? null : o.overallResult.name(),
                            o.overallResult == null ? "Observation" : "Observation: " + o.overallResult.label()))
                    .orElse(new LinkView("OBSERVATION", l.recordRootId, null, l.linkedOn, null, "Observation"));
        }
        return feedback.findByRootIdAndCurrentTrue(l.recordRootId)
                .map(f -> new LinkView("FEEDBACK", f.rootId, f.id, f.givenOn, null, "Principal feedback: " + f.category.label()))
                .orElse(new LinkView("FEEDBACK", l.recordRootId, null, l.linkedOn, null, "Principal feedback"));
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
