package com.hls.substitution.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Moves the open substitution requirements of a Teacher whose placement changes on a date (spec 027). Runs in the caller's
 * transaction.
 */
public interface PlacementSubstitution {

    /**
     * Requirements of the Teacher with a duty date on or after {@code from} that are not cancelled move to
     * {@code newSchoolId} (OPEN again if a substitute was assigned, who is told through {@code SubstitutionEnded}) and
     * {@code SubstitutionNeeded} is published for the new School. With a null School they are cancelled with the reason.
     * Returns the number changed.
     */
    int relocate(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId, String reason);
}
