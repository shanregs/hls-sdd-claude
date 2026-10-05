package com.hls.recruitment.api;

import java.util.Set;
import java.util.UUID;

/**
 * What the recruitment dashboard needs to know about induction. Defined here and implemented by the training module,
 * so recruitment never depends on training; when no implementation exists the dashboard shows zero inducted.
 */
public interface InductionProgress {

    /** Teachers who were signed off as having completed an induction. */
    Set<UUID> completedTeacherIds();
}
