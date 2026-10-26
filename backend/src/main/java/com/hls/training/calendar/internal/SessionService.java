package com.hls.training.calendar.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.training.api.TrainingSessionChanged;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes sessions: create, edit, move, publish, cancel and delete a draft (spec 024 US1). Every write is audited through
 * {@link ChangeRecorder}; a change to a Published session also appends to the session's own history, which keeps the
 * earlier date, time, trainer and venue visible. Callers have already checked the {@code TRAINING} grant.
 */
@Service("trainingSessionService")
public class SessionService {

    public static final String ENTITY = "TRAINING_SESSION";

    /** The body of a create or edit. {@code publish} is read on create only; {@code reason} is needed to move a session. */
    public record SessionBody(
            String type,
            String topic,
            LocalDate date,
            LocalTime startsAt,
            LocalTime endsAt,
            String trainerName,
            String trainerContact,
            String venueKind,
            String venue,
            Integer seatLimit,
            String description,
            Boolean publish,
            String reason,
            Long version) {}

    public record VersionBody(Long version) {}

    public record CancelBody(String reason, Long version) {}

    private final TrainingSessionRepository sessions;
    private final SessionHistoryRepository history;
    private final RegistrationRepository registrations;
    private final ReminderSentRepository reminders;
    private final SessionAudience audience;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;

    SessionService(
            TrainingSessionRepository sessions,
            SessionHistoryRepository history,
            RegistrationRepository registrations,
            ReminderSentRepository reminders,
            SessionAudience audience,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            TrainingClock clock) {
        this.sessions = sessions;
        this.history = history;
        this.registrations = registrations;
        this.reminders = reminders;
        this.audience = audience;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID create(Caller caller, SessionBody body) {
        String type = body.type();
        if (!TrainingSession.WEEKEND.equals(type) && !TrainingSession.MONTHLY.equals(type)) {
            throw new InvalidInputException("The type must be WEEKEND or MONTHLY.");
        }
        if (body.date() == null) {
            throw new InvalidInputException("The date is required.");
        }
        Instant now = clock.instant();
        TrainingSession session = new TrainingSession(caller.userId(), now);
        session.sessionType = type;
        apply(session, body, true);
        session.status = Boolean.TRUE.equals(body.publish()) ? TrainingSession.PUBLISHED : TrainingSession.DRAFT;
        sessions.saveAndFlush(session);
        changes.recordLifecycle(caller.userId(), ENTITY, session.id, "created", type + " on " + session.sessionDate + " (" + session.status + ")");
        return session.id;
    }

    @Transactional
    public UUID edit(Caller caller, UUID id, SessionBody body) {
        TrainingSession session = lock(id);
        StaleVersion.check(TrainingSession.class, id, session.version, body.version());
        if (!SessionRules.editable(session.status)) {
            throw new ConflictException("A " + session.status.toLowerCase() + " session can no longer be edited.");
        }
        if (body.type() != null && !body.type().equals(session.sessionType)) {
            throw new InvalidInputException("The type of a session cannot change.");
        }
        if (body.date() == null) {
            throw new InvalidInputException("The date is required.");
        }
        Snapshot before = Snapshot.of(session);
        apply(session, body, false);
        boolean moved = !before.date.equals(session.sessionDate)
                || !before.startsAt.equals(session.startsAt)
                || !before.endsAt.equals(session.endsAt);
        String reason = SessionRules.optional("The reason", body.reason(), SessionRules.REASON_MAX);
        if (moved && SessionRules.movable(session.status) && reason == null) {
            throw new InvalidInputException("A reason is required to move a session.");
        }
        long registered = registrations.countBySessionIdAndStatus(id, Registration.REGISTERED);
        if (session.seatLimit != null && registered > session.seatLimit) {
            throw new ConflictException("The seat limit cannot be lower than the " + registered + " people already registered.");
        }
        if (session.patternId != null) {
            session.handEdited = true;
        }
        session.updatedAt = clock.instant();
        sessions.saveAndFlush(session);

        boolean live = !TrainingSession.DRAFT.equals(session.status);
        Instant now = clock.instant();
        record(caller, session, live, now, "date", before.date, session.sessionDate, reason);
        record(caller, session, live, now, "time", before.time(), time(session.startsAt, session.endsAt), reason);
        record(caller, session, live, now, "trainer", before.trainerName, session.trainerName, reason);
        record(caller, session, live, now, "venue", before.venue, session.venue, reason);
        record(caller, session, live, now, "topic", before.topic, session.topic, reason);
        record(caller, session, live, now, "seat_limit", before.seatLimit, session.seatLimit, reason);
        changes.record(caller.userId(), ENTITY, id, "trainerContact", before.trainerContact, session.trainerContact);
        changes.record(caller.userId(), ENTITY, id, "venueKind", before.venueKind, session.venueKind);
        changes.record(caller.userId(), ENTITY, id, "description", before.description, session.description);

        if (moved && live) {
            reminders.deleteBySessionId(id);
            Set<UUID> users = audience.userIds(id);
            if (!users.isEmpty()) {
                events.publishEvent(new TrainingSessionChanged(id, "MOVED", users, before.date, session.sessionDate, session.topic));
            }
        }
        return id;
    }

    @Transactional
    public UUID publish(Caller caller, UUID id, VersionBody body) {
        TrainingSession session = lock(id);
        checkVersion(session, body == null ? null : body.version());
        if (!SessionRules.publishable(session.status)) {
            throw new ConflictException("Only a draft session can be published.");
        }
        String before = session.status;
        session.status = TrainingSession.PUBLISHED;
        session.updatedAt = clock.instant();
        sessions.saveAndFlush(session);
        changes.record(caller.userId(), ENTITY, id, "status", before, session.status);
        return id;
    }

    @Transactional
    public UUID cancel(Caller caller, UUID id, CancelBody body) {
        TrainingSession session = lock(id);
        checkVersion(session, body == null ? null : body.version());
        if (!SessionRules.cancellable(session.status)) {
            throw new ConflictException("Only a published session can be cancelled.");
        }
        String reason = SessionRules.required("A reason", body == null ? null : body.reason(), SessionRules.REASON_MAX);
        String before = session.status;
        session.status = TrainingSession.CANCELLED;
        session.cancelReason = reason;
        session.updatedAt = clock.instant();
        sessions.saveAndFlush(session);
        history.save(new SessionHistory(id, "status", before, session.status, reason, caller.userId(), clock.instant()));
        changes.record(caller.userId(), ENTITY, id, "status", before, session.status);
        reminders.deleteBySessionId(id);
        Set<UUID> users = audience.userIds(id);
        if (!users.isEmpty()) {
            events.publishEvent(new TrainingSessionChanged(id, "CANCELLED", users, session.sessionDate, null, session.topic));
        }
        return id;
    }

    @Transactional
    public void delete(Caller caller, UUID id) {
        TrainingSession session = lock(id);
        if (!SessionRules.deletable(session.status)) {
            throw new ConflictException("Only a draft session can be deleted.");
        }
        sessions.delete(session);
        sessions.flush();
        changes.recordLifecycle(caller.userId(), ENTITY, id, "deleted", session.topic + " on " + session.sessionDate);
    }

    // ------------------------------------------------------------------ helpers

    private TrainingSession lock(UUID id) {
        return sessions.findForUpdate(id).orElseThrow(() -> new NotFoundException("Session not found."));
    }

    private void checkVersion(TrainingSession session, Long version) {
        if (version != null) {
            StaleVersion.check(TrainingSession.class, session.id, session.version, version);
        }
    }

    /** Validates the body and copies it onto the session. */
    private void apply(TrainingSession session, SessionBody body, boolean creating) {
        SessionRules.checkTimes(body.startsAt(), body.endsAt());
        SessionRules.checkVenue(body.venueKind(), body.venue());
        session.topic = SessionRules.required("The topic", body.topic(), SessionRules.TOPIC_MAX);
        session.sessionDate = body.date();
        session.startsAt = body.startsAt();
        session.endsAt = body.endsAt();
        session.trainerName = SessionRules.optional("The trainer name", body.trainerName(), SessionRules.TRAINER_NAME_MAX);
        session.trainerContact = SessionRules.optional("The trainer contact", body.trainerContact(), SessionRules.TRAINER_CONTACT_MAX);
        session.venueKind = body.venueKind();
        session.venue = SessionRules.optional("The venue", body.venue(), SessionRules.VENUE_MAX);
        session.description = SessionRules.optional("The description", body.description(), SessionRules.DESCRIPTION_MAX);
        if (body.seatLimit() != null && body.seatLimit() < 1) {
            throw new InvalidInputException("The seat limit must be at least 1.");
        }
        session.seatLimit = TrainingSession.WEEKEND.equals(session.sessionType) ? body.seatLimit() : null;
    }

    private void record(Caller caller, TrainingSession session, boolean live, Instant now, String field, Object before, Object after, String reason) {
        if (Objects.equals(before, after)) {
            return;
        }
        if (live) {
            history.save(new SessionHistory(session.id, field, text(before), text(after), reason, caller.userId(), now));
        }
        changes.record(caller.userId(), ENTITY, session.id, field, before, after);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String time(LocalTime start, LocalTime end) {
        return start + "-" + end;
    }

    /** The editable fields before a change, for history and the move check. */
    private record Snapshot(
            LocalDate date, LocalTime startsAt, LocalTime endsAt, String trainerName, String trainerContact, String venueKind,
            String venue, String topic, Integer seatLimit, String description) {

        static Snapshot of(TrainingSession s) {
            return new Snapshot(s.sessionDate, s.startsAt, s.endsAt, s.trainerName, s.trainerContact, s.venueKind, s.venue, s.topic, s.seatLimit, s.description);
        }

        String time() {
            return SessionService.time(startsAt, endsAt);
        }
    }
}
