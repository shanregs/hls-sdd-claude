package com.hls.transfer.internal;

import com.hls.attendance.api.PlacementAttendance;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.schoolbilling.api.PlacementMoves;
import com.hls.schoolbilling.api.PlacementMoves.AppliedMove;
import com.hls.substitution.api.PlacementSubstitution;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import com.hls.transfer.api.TransferDecided;
import com.hls.transfer.api.TransferRequested;
import com.hls.transfer.internal.MovementChecks.Line;
import com.hls.transfer.internal.MovementChecks.Spec;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raising, changing, deciding and cancelling transfer and replacement requests (spec 027). Each operation is one
 * transaction: the placement change, the attendance leave re-tag and the substitution relocation commit together or not at
 * all. This class never writes placement tables; it goes through {@link PlacementMoves}.
 */
@Service
public class MovementService {

    static final String ENTITY = "MOVEMENT_REQUEST";
    static final String MOVED = "Teacher moved";

    public record TransferBody(UUID teacherId, UUID toSchoolId, UUID positionId, LocalDate effectiveOn, String reasonCategory, String note) {}

    public record ReplacementBody(
            UUID teacherId,
            UUID replacementTeacherId,
            LocalDate effectiveOn,
            UUID outgoingDestinationSchoolId,
            String reasonCategory,
            String note) {}

    /** A dry run of either body; {@code kind} is TRANSFER or REPLACEMENT. */
    public record CheckBody(
            String kind,
            UUID teacherId,
            UUID toSchoolId,
            UUID positionId,
            UUID replacementTeacherId,
            UUID outgoingDestinationSchoolId,
            LocalDate effectiveOn) {}

    public record ChangeBody(Long version, LocalDate effectiveOn, UUID positionId, UUID outgoingDestinationSchoolId, String note) {}

    public record DecisionBody(Long version, String reason) {}

    public record CheckResult(boolean ok, List<String> problems) {}

    private final MovementRequestRepository requests;
    private final MovementParticipantRepository participants;
    private final MovementAssignmentRepository links;
    private final MovementHistoryRepository history;
    private final MovementChecks checks;
    private final MovementScope scope;
    private final MovementFacts facts;
    private final MovementQueries queries;
    private final PlacementMoves moves;
    private final PlacementAttendance attendance;
    private final PlacementSubstitution substitution;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MovementService(
            MovementRequestRepository requests,
            MovementParticipantRepository participants,
            MovementAssignmentRepository links,
            MovementHistoryRepository history,
            MovementChecks checks,
            MovementScope scope,
            MovementFacts facts,
            MovementQueries queries,
            PlacementMoves moves,
            PlacementAttendance attendance,
            PlacementSubstitution substitution,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            Clock clock) {
        this.requests = requests;
        this.participants = participants;
        this.links = links;
        this.history = history;
        this.checks = checks;
        this.scope = scope;
        this.facts = facts;
        this.queries = queries;
        this.moves = moves;
        this.attendance = attendance;
        this.substitution = substitution;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ dry run

    @Transactional(readOnly = true)
    public CheckResult check(Caller caller, CheckBody body) {
        boolean replacement = "REPLACEMENT".equals(body.kind());
        if (!replacement && !"TRANSFER".equals(body.kind())) {
            throw new InvalidInputException("Kind must be TRANSFER or REPLACEMENT.");
        }
        UUID from = leavingSchool(caller, body.teacherId());
        Spec spec = new Spec(
                body.kind(),
                body.teacherId(),
                from,
                replacement ? from : body.toSchoolId(),
                body.positionId(),
                body.replacementTeacherId(),
                body.outgoingDestinationSchoolId(),
                body.effectiveOn());
        List<String> problems = checks.problems(checks.evaluate(spec, null));
        return new CheckResult(problems.isEmpty(), problems);
    }

    // ------------------------------------------------------------------ raise

    @Transactional
    public RequestView raiseTransfer(Caller caller, TransferBody body) {
        if (body == null || body.teacherId() == null || body.toSchoolId() == null || body.effectiveOn() == null) {
            throw new InvalidInputException("Teacher, receiving School and effective date are required.");
        }
        String category = MovementInput.category(body.reasonCategory());
        String note = MovementInput.note(category, body.note());
        UUID from = leavingSchool(caller, body.teacherId());
        Spec spec = new Spec("TRANSFER", body.teacherId(), from, body.toSchoolId(), body.positionId(), null, null, body.effectiveOn());
        refuseOnProblems(checks.evaluate(spec, null));
        MovementRequest r = new MovementRequest("TRANSFER", body.teacherId(), from, body.toSchoolId(), caller.userId(), clock.instant());
        r.setPositionId(body.positionId());
        fill(r, body.effectiveOn(), category, note);
        save(r, List.of(new MovementParticipant(r.getId(), body.teacherId(), "MOVING")));
        raised(caller, r);
        return queries.view(caller, r.getId());
    }

    @Transactional
    public RequestView raiseReplacement(Caller caller, ReplacementBody body) {
        if (body == null || body.teacherId() == null || body.replacementTeacherId() == null || body.effectiveOn() == null) {
            throw new InvalidInputException("Teacher, replacement Teacher and effective date are required.");
        }
        String category = MovementInput.category(body.reasonCategory());
        String note = MovementInput.note(category, body.note());
        UUID from = leavingSchool(caller, body.teacherId());
        Spec spec = new Spec(
                "REPLACEMENT", body.teacherId(), from, from, null, body.replacementTeacherId(), body.outgoingDestinationSchoolId(), body.effectiveOn());
        refuseOnProblems(checks.evaluate(spec, null));
        MovementRequest r = new MovementRequest("REPLACEMENT", body.teacherId(), from, from, caller.userId(), clock.instant());
        r.setReplacementTeacherId(body.replacementTeacherId());
        r.setOutgoingDestinationSchoolId(body.outgoingDestinationSchoolId());
        facts.currentPlacement(body.teacherId(), facts.today()).ifPresent(a -> r.setPositionId(a.positionId()));
        fill(r, body.effectiveOn(), category, note);
        save(r, List.of(
                new MovementParticipant(r.getId(), body.teacherId(), "OUTGOING"),
                new MovementParticipant(r.getId(), body.replacementTeacherId(), "REPLACEMENT")));
        raised(caller, r);
        return queries.view(caller, r.getId());
    }

    // ------------------------------------------------------------------ change

    @Transactional
    public RequestView changePending(Caller caller, UUID id, ChangeBody body) {
        MovementRequest r = load(caller, id);
        requireCanChange(caller, r);
        StaleVersion.check(MovementRequest.class, id, r.getVersion(), body == null ? null : body.version());
        requirePending(r);
        List<String> changed = new ArrayList<>();
        if (body.effectiveOn() != null && !body.effectiveOn().equals(r.getEffectiveOn())) {
            changed.add("effective date " + MovementInput.date(r.getEffectiveOn()) + " to " + MovementInput.date(body.effectiveOn()));
            r.setEffectiveOn(body.effectiveOn());
        }
        if (body.positionId() != null && !body.positionId().equals(r.getPositionId())) {
            if (!"TRANSFER".equals(r.getKind())) {
                throw new InvalidInputException("The position of a replacement is the one being filled.");
            }
            changed.add("position");
            r.setPositionId(body.positionId());
        }
        if (body.outgoingDestinationSchoolId() != null
                && !body.outgoingDestinationSchoolId().equals(r.getOutgoingDestinationSchoolId())) {
            if (!"REPLACEMENT".equals(r.getKind())) {
                throw new InvalidInputException("Only a replacement has a destination for the outgoing Teacher.");
            }
            changed.add("destination School");
            r.setOutgoingDestinationSchoolId(body.outgoingDestinationSchoolId());
        }
        if (body.note() != null) {
            String note = MovementInput.note(r.getReasonCategory(), body.note());
            if (!Objects.equals(note, r.getNote())) {
                changed.add("note");
                r.setNote(note);
            }
        }
        if (changed.isEmpty()) {
            throw new InvalidInputException("Nothing was changed.");
        }
        refuseOnProblems(checks.evaluate(Spec.of(r), r.getId()));
        r.touch(clock.instant());
        requests.saveAndFlush(r);
        addHistory(r.getId(), "CHANGED", "PENDING", "PENDING", caller.userId(), "Changed " + String.join(", ", changed) + ".");
        changes.record(caller.userId(), ENTITY, r.getId(), "changed", null, String.join(", ", changed));
        return queries.view(caller, r.getId());
    }

    // ------------------------------------------------------------------ decide

    @Transactional
    public RequestView approve(Caller caller, UUID id, DecisionBody body) {
        MovementRequest r = load(caller, id);
        requireCanDecide(caller, r);
        StaleVersion.check(MovementRequest.class, id, r.getVersion(), body == null ? null : body.version());
        requirePending(r);
        Spec spec = Spec.of(r);
        refuseOnProblems(checks.evaluate(spec, r.getId()));
        AppliedMove applied;
        try {
            applied = moves.apply(caller.userId(), spec.toMoveSpec());
        } catch (ConflictException e) {
            throw new MovementRefusedException(List.of(e.getMessage()));
        }
        link(r, applied);
        afterApply(caller, r);
        String before = r.getStatus();
        Instant now = clock.instant();
        r.decide("APPROVED", caller.userId(), null, now);
        closeParticipants(r.getId());
        requests.saveAndFlush(r);
        addHistory(r.getId(), "APPROVED", before, "APPROVED", caller.userId(), null);
        changes.record(caller.userId(), ENTITY, r.getId(), "status", before, "APPROVED");
        publishDecided(r, "APPROVED", caller.userId());
        return queries.view(caller, r.getId());
    }

    @Transactional
    public RequestView reject(Caller caller, UUID id, DecisionBody body) {
        MovementRequest r = load(caller, id);
        requireCanDecide(caller, r);
        StaleVersion.check(MovementRequest.class, id, r.getVersion(), body == null ? null : body.version());
        requirePending(r);
        String reason = MovementInput.requiredReason(body.reason(), "rejection");
        finish(caller, r, "REJECTED", reason);
        return queries.view(caller, r.getId());
    }

    @Transactional
    public RequestView cancel(Caller caller, UUID id, DecisionBody body) {
        MovementRequest r = load(caller, id);
        requireCanChange(caller, r);
        StaleVersion.check(MovementRequest.class, id, r.getVersion(), body == null ? null : body.version());
        String reason = MovementInput.requiredReason(body.reason(), "cancellation");
        if ("PENDING".equals(r.getStatus())) {
            finish(caller, r, "CANCELLED", reason);
        } else if ("APPROVED".equals(r.getStatus())) {
            if (!r.getEffectiveOn().isAfter(facts.today())) {
                throw new ConflictException("This request has already taken effect and cannot be cancelled.");
            }
            reverseApproved(caller, r);
            finish(caller, r, "CANCELLED", reason);
        } else {
            throw new ConflictException("This request is already " + r.getStatus().toLowerCase() + ".");
        }
        return queries.view(caller, r.getId());
    }

    // ------------------------------------------------------------------ helpers

    /** The School the Teacher is placed at today, which must be one the caller acts for. */
    private UUID leavingSchool(Caller caller, UUID teacherId) {
        if (teacherId == null) {
            throw new InvalidInputException("Choose the Teacher.");
        }
        if (facts.teacher(teacherId).isEmpty()) {
            throw new NotFoundException("Teacher not found.");
        }
        Assignment current = facts.currentPlacement(teacherId, facts.today()).orElse(null);
        if (current == null) {
            if (!caller.orgWide()) {
                throw new NotFoundException("Teacher not found.");
            }
            throw new MovementRefusedException(List.of("The Teacher is not placed at a School."));
        }
        scope.requireSchool(caller, current.schoolId(), "Teacher");
        return current.schoolId();
    }

    private void fill(MovementRequest r, LocalDate effectiveOn, String category, String note) {
        r.setEffectiveOn(effectiveOn);
        r.setReasonCategory(category);
        r.setNote(note);
    }

    private void save(MovementRequest r, List<MovementParticipant> people) {
        try {
            requests.saveAndFlush(r);
            participants.saveAll(people);
            participants.flush();
        } catch (DataIntegrityViolationException e) {
            throw new MovementRefusedException(
                    List.of("A Teacher in this request is already in another open transfer or replacement request."));
        }
    }

    private void raised(Caller caller, MovementRequest r) {
        addHistory(r.getId(), "RAISED", null, "PENDING", caller.userId(), null);
        changes.recordLifecycle(caller.userId(), ENTITY, r.getId(), "created", r.getKind() + " on " + r.getEffectiveOn());
        events.publishEvent(new TransferRequested(
                r.getId(), r.getKind(), r.getTeacherId(), r.getFromSchoolId(), r.getToSchoolId(), r.getRequestedBy(), r.getEffectiveOn()));
    }

    private MovementRequest load(Caller caller, UUID id) {
        MovementRequest r = requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found."));
        scope.requireRead(caller, r);
        return r;
    }

    private void requirePending(MovementRequest r) {
        if (!"PENDING".equals(r.getStatus())) {
            throw new ConflictException("This request is already " + r.getStatus().toLowerCase() + ".");
        }
    }

    /** Approve and reject: the Approve grant, the leaving School's scope, and never the requester. */
    private void requireCanDecide(Caller caller, MovementRequest r) {
        if (!caller.canApprove()) {
            throw new ForbiddenFieldException("You do not have the right to decide transfer requests.");
        }
        scope.requireWrite(caller, r);
        if (r.getRequestedBy().equals(caller.userId())) {
            throw new ForbiddenFieldException("You cannot decide a request you raised yourself.");
        }
    }

    /** Change and cancel: the Edit grant and the leaving School's scope, and the requester or someone who may decide. */
    private void requireCanChange(Caller caller, MovementRequest r) {
        if (!caller.canEdit()) {
            throw new ForbiddenFieldException("You do not have the right to change transfer requests.");
        }
        scope.requireWrite(caller, r);
        if (!r.getRequestedBy().equals(caller.userId()) && !caller.canApprove()) {
            throw new ForbiddenFieldException("Only the person who raised the request or a decider can do this.");
        }
    }

    private void refuseOnProblems(List<Line> lines) {
        List<String> problems = checks.problems(lines);
        if (!problems.isEmpty()) {
            throw new MovementRefusedException(problems);
        }
    }

    private void link(MovementRequest r, AppliedMove applied) {
        for (UUID ended : applied.endedAssignmentIds()) {
            links.save(new MovementAssignment(r.getId(), ended, r.getTeacherId(), "ENDED"));
        }
        List<UUID> created = applied.createdAssignmentIds();
        for (int i = 0; i < created.size(); i++) {
            // a transfer creates the moving Teacher's row; a replacement creates the incoming row, then the outgoing one
            UUID teacher = "TRANSFER".equals(r.getKind()) || i > 0 ? r.getTeacherId() : r.getReplacementTeacherId();
            links.save(new MovementAssignment(r.getId(), created.get(i), teacher, "CREATED"));
        }
        links.flush();
    }

    /** Leave marks and open substitution requirements follow the Teacher across the date, in the same transaction. */
    private void afterApply(Caller caller, MovementRequest r) {
        UUID actor = caller.userId();
        LocalDate on = r.getEffectiveOn();
        if ("TRANSFER".equals(r.getKind())) {
            attendance.retagLeaveMarks(actor, r.getTeacherId(), on, r.getToSchoolId());
            substitution.relocate(actor, r.getTeacherId(), on, r.getToSchoolId(), MOVED);
            return;
        }
        attendance.retagLeaveMarks(actor, r.getReplacementTeacherId(), on, r.getFromSchoolId());
        if (r.getOutgoingDestinationSchoolId() != null) {
            attendance.retagLeaveMarks(actor, r.getTeacherId(), on, r.getOutgoingDestinationSchoolId());
            substitution.relocate(actor, r.getTeacherId(), on, r.getOutgoingDestinationSchoolId(), MOVED);
        } else {
            // no destination: their leave days after the date stay with the old School (spec 027 open item default 1)
            substitution.relocate(actor, r.getTeacherId(), on, null, MOVED);
        }
    }

    private void reverseApproved(Caller caller, MovementRequest r) {
        List<MovementAssignment> rows = links.findByRequestId(r.getId());
        List<UUID> ended = rows.stream().filter(l -> "ENDED".equals(l.getEffect())).map(MovementAssignment::getAssignmentId).toList();
        List<UUID> created = rows.stream().filter(l -> "CREATED".equals(l.getEffect())).map(MovementAssignment::getAssignmentId).toList();
        try {
            moves.reverse(caller.userId(), new AppliedMove(ended, created));
        } catch (ConflictException e) {
            throw new MovementRefusedException(List.of(e.getMessage()));
        }
        UUID actor = caller.userId();
        LocalDate on = r.getEffectiveOn();
        String why = "Move cancelled";
        if ("TRANSFER".equals(r.getKind())) {
            attendance.retagLeaveMarks(actor, r.getTeacherId(), on, r.getFromSchoolId());
            substitution.relocate(actor, r.getTeacherId(), on, r.getFromSchoolId(), why);
        } else if (r.getOutgoingDestinationSchoolId() != null) {
            attendance.retagLeaveMarks(actor, r.getTeacherId(), on, r.getFromSchoolId());
            substitution.relocate(actor, r.getTeacherId(), on, r.getFromSchoolId(), why);
        }
    }

    private void finish(Caller caller, MovementRequest r, String status, String reason) {
        String before = r.getStatus();
        r.decide(status, caller.userId(), reason, clock.instant());
        closeParticipants(r.getId());
        requests.saveAndFlush(r);
        addHistory(r.getId(), status, before, status, caller.userId(), reason);
        changes.record(caller.userId(), ENTITY, r.getId(), "status", before, status);
        publishDecided(r, status, caller.userId());
    }

    private void closeParticipants(UUID requestId) {
        List<MovementParticipant> open = participants.findByRequestId(requestId);
        open.forEach(MovementParticipant::close);
        participants.saveAll(open);
    }

    private void publishDecided(MovementRequest r, String outcome, UUID decidedBy) {
        events.publishEvent(new TransferDecided(
                r.getId(),
                r.getKind(),
                outcome,
                r.getTeacherId(),
                r.getReplacementTeacherId(),
                r.getFromSchoolId(),
                r.getToSchoolId(),
                r.getRequestedBy(),
                decidedBy,
                r.getEffectiveOn()));
    }

    private void addHistory(UUID requestId, String action, String from, String to, UUID actor, String note) {
        history.save(new MovementRequestHistory(requestId, action, from, to, actor, note, clock.instant()));
    }
}
