package com.hls.training.calendar.internal;

import java.util.List;
import org.springframework.stereotype.Component;

/** Placeholder until the assignment service (US3) supplies the real count. */
@Component
class NoGroupGaps implements GroupGaps {

    @Override
    public Integer notFullyAssigned(TrainingSession session, List<Assignment> assigned) {
        return null;
    }
}
