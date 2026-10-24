package com.hls.teacher.api;

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
}
