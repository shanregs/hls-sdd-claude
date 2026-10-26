package com.hls.training.calendar.internal;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "N people in the group are not assigned" (spec 024 FR-011): the session's group is its pattern's default group, or the
 * group most recently used to assign people. The count is resolved on read; nothing is added by itself.
 */
@Component("trainingGroupGaps")
class GroupGapsImpl implements GroupGaps {

    private final PatternRepository patterns;
    private final GroupResolver groups;
    private final TrainingClock clock;

    GroupGapsImpl(PatternRepository patterns, GroupResolver groups, TrainingClock clock) {
        this.patterns = patterns;
        this.groups = groups;
        this.clock = clock;
    }

    @Override
    public Integer notFullyAssigned(TrainingSession session, List<Assignment> assigned) {
        String kind = null;
        java.util.UUID ref = null;
        Optional<Assignment> viaGroup = assigned.stream()
                .filter(a -> a.groupKind != null)
                .max(Comparator.comparing((Assignment a) -> a.assignedAt));
        if (viaGroup.isPresent()) {
            kind = viaGroup.get().groupKind;
            ref = viaGroup.get().groupRef;
        } else if (session.patternId != null) {
            TrainingPattern pattern = patterns.findById(session.patternId).orElse(null);
            if (pattern != null) {
                kind = pattern.defaultGroupKind;
                ref = pattern.defaultGroupRef;
            }
        }
        if (kind == null) {
            return null;
        }
        GroupResolver.Resolved resolved = groups.group(kind, ref, clock.today());
        Set<java.util.UUID> have = new HashSet<>();
        assigned.stream().filter(Assignment::isRequired).forEach(a -> have.add(a.personId));
        long missing = resolved.teacherIds().stream().filter(id -> !have.contains(id)).count()
                + resolved.managerIds().stream().filter(id -> !have.contains(id)).count();
        return missing == 0 ? null : (int) missing;
    }
}
