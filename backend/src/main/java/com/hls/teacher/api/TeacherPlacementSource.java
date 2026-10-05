package com.hls.teacher.api;

import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where a Teacher is assigned, owned by {@code teacher} and implemented by {@code schoolbilling} (spec 012
 * research.md section 1): the School contract replaces the interim placement of spec 005, and this interface
 * keeps the dependency pointing one way so {@code teacher} never depends on {@code schoolbilling}.
 *
 * <p>Rows are dated and never overwritten ({@code ACTIVE}, {@code CANCELLED}, {@code CORRECTED}); "current"
 * is always evaluated against a date, so a future start schedules a move.
 */
public interface TeacherPlacementSource {

    /**
     * One assignment row. {@code positionId} and {@code positionNumber} are null for an assignment that is
     * not yet mapped to a position of a School contract.
     */
    record Assignment(
            UUID id,
            UUID teacherId,
            UUID schoolId,
            UUID positionId,
            Integer positionNumber,
            LocalDate startsOn,
            LocalDate endsOn,
            String status) {}

    /** ACTIVE assignments of the given Teachers overlapping {@code from..to} inclusive, oldest first. */
    List<PlacementSpan> spansOverlapping(Collection<UUID> teacherIds, LocalDate from, LocalDate to);

    /** Ids of Teachers with an ACTIVE assignment overlapping {@code from..to} inclusive. */
    Set<UUID> teachersAssignedDuring(LocalDate from, LocalDate to);

    /** True if any ACTIVE assignment at the School is current on {@code from} or still to come. */
    boolean hasCurrentOrFutureAssignment(UUID schoolId, LocalDate from);

    /** Teachers in effect on {@code on}, counted per School. */
    Map<UUID, Long> teacherCountsBySchool(Collection<UUID> schoolIds, LocalDate on);

    /** Ids of Teachers whose ACTIVE assignment in effect on {@code on} is at one of the Schools. */
    Set<UUID> teacherIdsAtSchoolsOn(Collection<UUID> schoolIds, LocalDate on);

    /** The ACTIVE assignment in effect on {@code on}, per Teacher. */
    Map<UUID, Assignment> currentOf(Collection<UUID> teacherIds, LocalDate on);

    /** The scheduled (future) ACTIVE assignment after {@code on}, per Teacher. */
    Map<UUID, Assignment> pendingOf(Collection<UUID> teacherIds, LocalDate on);

    /** Every assignment row of the Teacher, newest first, whatever its status. */
    List<Assignment> historyOf(UUID teacherId);

    /**
     * Assigns or moves the Teacher to {@code schoolId} from {@code effectiveOn} (default today). A future date
     * schedules the move. {@code positionId} may be null (see spec 012 FR-005 and FR-006).
     */
    void assign(UUID actor, UUID teacherId, UUID schoolId, UUID positionId, LocalDate effectiveOn);

    /** Cancels the scheduled future move and restores the current assignment's open end. */
    void cancelPending(UUID actor, UUID teacherId);

    /** Ends the current assignment on the exit date and cancels a scheduled one (Teacher exit). */
    void endForExit(UUID teacherId, LocalDate exitDate);
}
