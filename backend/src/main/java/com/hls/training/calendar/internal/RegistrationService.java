package com.hls.training.calendar.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.training.api.TrainingRegistered;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration of Teachers for open weekend sessions (spec 024 US1). The seat limit is enforced under a pessimistic lock on
 * the session row, so two simultaneous requests can never pass it (Constitution Principle VIII); the (session, Teacher) row
 * is unique, so registering twice is one registration.
 */
@Service("trainingRegistrationService")
public class RegistrationService {

    static final String ENTITY = "TRAINING_REGISTRATION";

    /** Whether the call created a registration or found one already in place. */
    public record Result(UUID sessionId, UUID teacherId, String status, boolean created, Integer seatsLeft) {}

    /** One row of the registration roster. */
    public record RosterRow(UUID teacherId, String name, String status, Instant registeredAt, UUID registeredBy, Instant cancelledAt) {}

    private final TrainingSessionRepository sessions;
    private final RegistrationRepository registrations;
    private final PeopleDirectory people;
    private final TrainingScope scopes;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;

    RegistrationService(
            TrainingSessionRepository sessions,
            RegistrationRepository registrations,
            PeopleDirectory people,
            TrainingScope scopes,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            TrainingClock clock) {
        this.sessions = sessions;
        this.registrations = registrations;
        this.people = people;
        this.scopes = scopes;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registers a Teacher. {@code teacherId} is null when a Teacher registers themselves; Admin and Director pass the Teacher
     * (the controller has checked the matching grant).
     */
    @Transactional
    public Result register(Caller caller, UUID sessionId, UUID teacherId) {
        TrainingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        UUID teacher = target(caller, teacherId);
        if (teacherId == null && TrainingSession.DRAFT.equals(session.status)) {
            throw new NotFoundException("Session not found.");
        }
        if (!TrainingSession.WEEKEND.equals(session.sessionType)) {
            throw new ConflictException("Only weekend refresher sessions take registrations.");
        }
        if (!SessionRules.takesPeople(session.status)) {
            throw new ConflictException("This session is not open for registration.");
        }
        Registration existing = registrations.findBySessionIdAndTeacherId(sessionId, teacher).orElse(null);
        if (existing != null && existing.isRegistered()) {
            return new Result(sessionId, teacher, existing.status, false, seatsLeft(session));
        }
        if (clock.started(session.sessionDate, session.startsAt)) {
            throw new ConflictException("This session has already started.");
        }
        Person person = people.teachers(List.of(teacher)).get(teacher);
        if (person == null) {
            throw new NotFoundException("Teacher not found.");
        }
        if (!person.active()) {
            throw new ConflictException("Only active Teachers can register. " + person.name() + " is " + person.status().toLowerCase().replace('_', ' ') + ".");
        }
        if (session.seatLimit != null && registrations.countBySessionIdAndStatus(sessionId, Registration.REGISTERED) >= session.seatLimit) {
            throw new ConflictException("There are no seats left in this session.");
        }
        Instant now = clock.instant();
        Registration row;
        if (existing == null) {
            row = new Registration(sessionId, teacher, caller.userId(), now);
        } else {
            row = existing;
            row.status = Registration.REGISTERED;
            row.registeredBy = caller.userId();
            row.registeredAt = now;
            row.cancelledAt = null;
            row.cancelledBy = null;
        }
        registrations.saveAndFlush(row);
        changes.record(caller.userId(), ENTITY, row.id, "status", existing == null ? null : Registration.CANCELLED, Registration.REGISTERED);
        if (person.userId() != null) {
            events.publishEvent(new TrainingRegistered(sessionId, person.userId(), session.sessionDate, session.topic));
        }
        return new Result(sessionId, teacher, Registration.REGISTERED, true, seatsLeft(session));
    }

    /** Cancels a registration before the session starts; {@code teacherId} is null for the Teacher's own. */
    @Transactional
    public Result cancel(Caller caller, UUID sessionId, UUID teacherId) {
        TrainingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        UUID teacher = target(caller, teacherId);
        Registration row = registrations.findBySessionIdAndTeacherId(sessionId, teacher).orElse(null);
        if (row == null || !row.isRegistered()) {
            throw new NotFoundException("Registration not found.");
        }
        if (clock.started(session.sessionDate, session.startsAt)) {
            throw new ConflictException("The session has started, so the registration can no longer be cancelled.");
        }
        row.status = Registration.CANCELLED;
        row.cancelledAt = clock.instant();
        row.cancelledBy = caller.userId();
        registrations.saveAndFlush(row);
        changes.record(caller.userId(), ENTITY, row.id, "status", Registration.REGISTERED, Registration.CANCELLED);
        return new Result(sessionId, teacher, Registration.CANCELLED, false, seatsLeft(session));
    }

    /** The registration roster; a Manager sees only the Teachers placed under them. */
    @Transactional(readOnly = true)
    public List<RosterRow> roster(Caller caller, UUID sessionId) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        if (!caller.orgWide() && TrainingSession.DRAFT.equals(session.status)) {
            throw new NotFoundException("Session not found.");
        }
        TrainingScope.Scope scope = scopes.of(caller);
        List<Registration> rows = registrations.findBySessionId(sessionId).stream()
                .filter(r -> scope.allowsTeacher(r.teacherId))
                .toList();
        Map<UUID, Person> names = people.teachers(rows.stream().map(r -> r.teacherId).toList());
        List<RosterRow> out = new ArrayList<>();
        for (Registration r : rows) {
            Person person = names.get(r.teacherId);
            out.add(new RosterRow(r.teacherId, person == null ? "Unknown" : person.name(), r.status, r.registeredAt, r.registeredBy, r.cancelledAt));
        }
        out.sort(Comparator.comparing(RosterRow::registeredAt));
        return out;
    }

    private Integer seatsLeft(TrainingSession session) {
        return session.seatLimit == null
                ? null
                : Math.max(0, session.seatLimit - (int) registrations.countBySessionIdAndStatus(session.id, Registration.REGISTERED));
    }

    /** The Teacher acted on: the named one for Admin or Director, otherwise the caller's own record. */
    private UUID target(Caller caller, UUID teacherId) {
        if (teacherId != null) {
            if (!caller.orgWide()) {
                throw new InvalidInputException("Only an Admin or Director can act for another Teacher.");
            }
            return teacherId;
        }
        UUID own = scopes.of(caller).ownTeacherId();
        if (own == null) {
            throw new NotFoundException("Teacher not found.");
        }
        return own;
    }
}
