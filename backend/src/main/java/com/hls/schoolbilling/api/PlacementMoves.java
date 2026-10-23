package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Moves of Teachers between Schools as dated placement rows, for spec 027 (transfer and replacement). The only way
 * {@code transfer} changes placements: it never writes the placement tables itself. {@link #check} and {@link #apply}
 * follow the same rules; {@code apply} runs in the caller's transaction and is all or nothing.
 *
 * <p>Rules: the outgoing row ends the day before {@code effectiveOn}; the incoming row starts on it; a replacement's
 * incoming row reuses the outgoing row's position; no day has two Schools or, apart from an outgoing Teacher with no
 * destination, none.
 */
public interface PlacementMoves {

    enum Kind {
        TRANSFER,
        REPLACEMENT
    }

    /**
     * @param outgoingTeacherId the moving Teacher (transfer) or the Teacher being replaced (replacement)
     * @param schoolId the School the outgoing Teacher is placed at
     * @param incomingTeacherId replacement only
     * @param toSchoolId transfer only: the receiving School
     * @param positionId transfer only: a named receiving position, else null
     * @param outgoingDestinationSchoolId replacement only: where the replaced Teacher goes, null means available
     */
    record MoveSpec(
            Kind kind,
            UUID outgoingTeacherId,
            UUID schoolId,
            UUID incomingTeacherId,
            UUID toSchoolId,
            UUID positionId,
            UUID outgoingDestinationSchoolId,
            LocalDate effectiveOn) {}

    record MoveCheck(boolean ok, List<String> problems) {}

    record AppliedMove(List<UUID> endedAssignmentIds, List<UUID> createdAssignmentIds) {}

    record VacantPosition(UUID positionId, int number, BigDecimal salary) {}

    /** A dry run with the same rules as {@link #apply}; writes nothing. */
    MoveCheck check(MoveSpec spec);

    /** Applies the move in the caller's transaction; a {@code ConflictException} on any failure leaves nothing written. */
    AppliedMove apply(UUID actorUserId, MoveSpec spec);

    /** Cancels created rows still in the future and restores the open end of ended rows; refuses a row that has started. */
    void reverse(UUID actorUserId, AppliedMove move);

    /** Vacant positions of the School's contract in effect on {@code on}; empty for a School with no MoU yet. */
    List<VacantPosition> vacantPositions(UUID schoolId, LocalDate on);
}
