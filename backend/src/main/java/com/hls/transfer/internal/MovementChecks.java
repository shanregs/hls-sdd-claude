package com.hls.transfer.internal;

import com.hls.attendance.api.PlacementAttendance;
import com.hls.schoolbilling.api.PlacementMoves;
import com.hls.schoolbilling.api.PlacementMoves.Kind;
import com.hls.schoolbilling.api.PlacementMoves.MoveCheck;
import com.hls.schoolbilling.api.PlacementMoves.MoveSpec;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one rule set for raise, change, approve and the dry run (spec 027 data-model.md "Validation rules"). Placement rules
 * are delegated to {@link PlacementMoves#check}; this class adds the request rules around them.
 */
@Component
class MovementChecks {

    /** What a request asks for; {@code fromSchoolId} is the School the outgoing Teacher is placed at. */
    record Spec(
            String kind,
            UUID teacherId,
            UUID fromSchoolId,
            UUID toSchoolId,
            UUID positionId,
            UUID replacementTeacherId,
            UUID outgoingDestinationSchoolId,
            LocalDate effectiveOn) {

        static Spec of(MovementRequest r) {
            return new Spec(
                    r.getKind(),
                    r.getTeacherId(),
                    r.getFromSchoolId(),
                    r.getToSchoolId(),
                    "TRANSFER".equals(r.getKind()) ? r.getPositionId() : null,
                    r.getReplacementTeacherId(),
                    r.getOutgoingDestinationSchoolId(),
                    r.getEffectiveOn());
        }

        boolean replacement() {
            return "REPLACEMENT".equals(kind);
        }

        MoveSpec toMoveSpec() {
            return new MoveSpec(
                    replacement() ? Kind.REPLACEMENT : Kind.TRANSFER,
                    teacherId,
                    fromSchoolId,
                    replacementTeacherId,
                    replacement() ? null : toSchoolId,
                    replacement() ? null : positionId,
                    outgoingDestinationSchoolId,
                    effectiveOn);
        }
    }

    /** One line of the live check list: a rule that holds or a problem in plain words. */
    record Line(boolean ok, String text) {}

    private final MovementFacts facts;
    private final PlacementMoves moves;
    private final PlacementAttendance attendance;
    private final MovementParticipantRepository participants;

    MovementChecks(
            MovementFacts facts,
            PlacementMoves moves,
            PlacementAttendance attendance,
            MovementParticipantRepository participants) {
        this.facts = facts;
        this.moves = moves;
        this.attendance = attendance;
        this.participants = participants;
    }

    /** Every rule, in order; {@code ownRequestId} is excluded from the one-open-request rule (change and approve). */
    List<Line> evaluate(Spec spec, UUID ownRequestId) {
        List<Line> lines = new ArrayList<>();
        LocalDate today = facts.today();
        LocalDate on = spec.effectiveOn();
        if (on == null) {
            lines.add(new Line(false, "An effective date is required."));
            return lines;
        }
        if (on.isBefore(today)) {
            lines.add(new Line(false, "The effective date " + MovementInput.date(on) + " has passed; choose today or a later date."));
        } else {
            lines.add(new Line(true, "The effective date is today or later."));
        }
        Assignment current = facts.currentPlacement(spec.teacherId(), today).orElse(null);
        if (current == null || !current.schoolId().equals(spec.fromSchoolId())) {
            lines.add(new Line(false, "The Teacher is no longer placed at the leaving School."));
        } else {
            lines.add(new Line(true, "The Teacher is placed at the leaving School."));
        }
        Set<UUID> involved = new java.util.HashSet<>();
        involved.add(spec.teacherId());
        if (spec.replacementTeacherId() != null) {
            involved.add(spec.replacementTeacherId());
        }
        boolean busy = participants.findByTeacherIdInAndOpenTrue(involved).stream()
                .anyMatch(p -> !Objects.equals(p.getRequestId(), ownRequestId));
        lines.add(busy
                ? new Line(false, "A Teacher in this request is already in another open transfer or replacement request.")
                : new Line(true, "No other open request involves these Teachers."));
        if (!on.isBefore(today)) {
            if (attendance.hasNonLeaveMarkOnOrAfter(spec.teacherId(), on)) {
                lines.add(new Line(false, "The Teacher already has attendance marked on or after " + MovementInput.date(on)
                        + ", so the date cannot move earlier than that."));
            } else {
                lines.add(new Line(true, "No attendance is marked on or after the effective date."));
            }
            if (attendance.anyMonthLocked(spec.teacherId(), on)) {
                lines.add(new Line(false, "The Teacher's attendance month from " + MovementInput.date(on) + " is locked."));
            } else {
                lines.add(new Line(true, "No locked attendance month is affected."));
            }
        }
        MoveCheck placement = moves.check(spec.toMoveSpec());
        if (placement.ok()) {
            lines.add(new Line(true, "The placement change is possible on " + MovementInput.date(on) + "."));
        } else {
            placement.problems().forEach(p -> lines.add(new Line(false, p)));
        }
        return lines;
    }

    List<String> problems(List<Line> lines) {
        return lines.stream().filter(l -> !l.ok()).map(Line::text).toList();
    }
}
