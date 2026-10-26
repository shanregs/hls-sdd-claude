package com.hls.training.calendar.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.training.api.TrainingExceptionRaised;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-compliance exceptions (spec 024 US5, FR-019): an assigned person recorded Absent, and not on leave, raises one
 * exception; correcting the absence withdraws it and correcting back reopens the same row. A registered no-show raises
 * none, and nor does a person who was on approved leave.
 */
@Service("trainingExceptionService")
public class ExceptionService {

    static final String ENTITY = "TRAINING_EXCEPTION";

    private final ExceptionRepository exceptions;
    private final PeopleDirectory people;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;

    ExceptionService(
            ExceptionRepository exceptions,
            PeopleDirectory people,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            TrainingClock clock) {
        this.exceptions = exceptions;
        this.people = people;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    /** Called in the transaction that saves an attendance entry: raises, reopens or withdraws the exception. */
    @Transactional
    public void onEntrySaved(TrainingSession session, AttendanceEntry entry, boolean assigned) {
        TrainingException existing = exceptions
                .findBySessionIdAndPersonKindAndPersonId(session.id, entry.personKind, entry.personId)
                .orElse(null);
        boolean raises = AttendanceEntry.ABSENT.equals(entry.outcome) && assigned && !entry.onLeave;
        if (raises) {
            if (existing == null) {
                TrainingException created = new TrainingException(session.id, entry.personKind, entry.personId, entry.id, clock.instant());
                exceptions.saveAndFlush(created);
                changes.recordLifecycle(entry.recordedBy, ENTITY, created.id, "raised", session.topic + " on " + session.sessionDate);
                announce(session, entry);
            } else if (TrainingException.WITHDRAWN.equals(existing.status)) {
                existing.status = TrainingException.OPEN;
                existing.closureKind = null;
                existing.closureNote = null;
                existing.makeupSessionId = null;
                existing.closedBy = null;
                existing.closedAt = null;
                existing.entryId = entry.id;
                exceptions.saveAndFlush(existing);
                changes.record(entry.recordedBy, ENTITY, existing.id, "status", TrainingException.WITHDRAWN, TrainingException.OPEN);
                announce(session, entry);
            }
        } else if (existing != null && !TrainingException.WITHDRAWN.equals(existing.status)) {
            String before = existing.status;
            existing.status = TrainingException.WITHDRAWN;
            exceptions.saveAndFlush(existing);
            changes.record(entry.recordedBy, ENTITY, existing.id, "status", before, TrainingException.WITHDRAWN);
        }
    }

    private void announce(TrainingSession session, AttendanceEntry entry) {
        Person person = people.person(entry.personKind, entry.personId);
        events.publishEvent(new TrainingExceptionRaised(session.id, person == null ? "Unknown" : person.name(), session.sessionDate));
    }
}
