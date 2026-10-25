package com.hls.training.calendar.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Who is told about a session: the people required at it and the Teachers registered for it. */
@Component
class SessionAudience {

    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final PeopleDirectory people;

    SessionAudience(AssignmentRepository assignments, RegistrationRepository registrations, PeopleDirectory people) {
        this.assignments = assignments;
        this.registrations = registrations;
        this.people = people;
    }

    /** User accounts of everyone required at or registered for the session; people without an account are skipped. */
    Set<UUID> userIds(UUID sessionId) {
        Set<UUID> teacherIds = new HashSet<>();
        Set<UUID> managerIds = new HashSet<>();
        for (Assignment a : assignments.findBySessionIdAndStatus(sessionId, Assignment.REQUIRED)) {
            (Assignment.MANAGER.equals(a.personKind) ? managerIds : teacherIds).add(a.personId);
        }
        for (Registration r : registrations.findBySessionId(sessionId)) {
            if (r.isRegistered()) {
                teacherIds.add(r.teacherId);
            }
        }
        return people.userIds(teacherIds, managerIds);
    }

    /** The people (both kinds) required at the session, keyed by kind and id. */
    List<Assignment> required(UUID sessionId) {
        return assignments.findBySessionIdAndStatus(sessionId, Assignment.REQUIRED);
    }
}
