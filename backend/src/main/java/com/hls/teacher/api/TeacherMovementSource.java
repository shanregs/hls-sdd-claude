package com.hls.teacher.api;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which Teachers have been transferred or replaced, owned by {@code teacher} and implemented by {@code transfer}
 * (spec 027) so that {@code teacher} never depends on it. The Teacher list intersects these with the caller's scope.
 */
public interface TeacherMovementSource {

    /** Teachers with an Approved or Applied transfer, or two ACTIVE placement rows at different Schools. */
    Set<UUID> transferredTeacherIds();

    /** Outgoing Teachers of an Approved or Applied replacement. */
    Set<UUID> replacedTeacherIds();

    /** Transfers and replacements counted for a Teacher (the outgoing Teacher of a replacement), spec 014c A5. */
    record Movements(int transfers, int replacements) {}

    /**
     * Per Teacher, the Approved or Applied (in force) transfer and replacement requests whose effective date falls in
     * {@code from..to} inclusive. Draft, Rejected and Cancelled requests are not counted; Teachers with none are absent.
     */
    Map<UUID, Movements> movementsBetween(LocalDate from, LocalDate to);
}
