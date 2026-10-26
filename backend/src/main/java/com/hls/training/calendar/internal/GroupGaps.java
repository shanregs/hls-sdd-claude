package com.hls.training.calendar.internal;

import java.util.List;

/** Counts people of a session's saved target group who are not assigned yet (the real answer arrives with the assignment service). */
interface GroupGaps {

    /** Null when the session has no saved group or the count is not known. */
    Integer notFullyAssigned(TrainingSession session, List<Assignment> assigned);
}
