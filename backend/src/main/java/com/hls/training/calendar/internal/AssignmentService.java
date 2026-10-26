package com.hls.training.calendar.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.training.api.TrainingAssigned;
import com.hls.training.api.TrainingAssignmentRemoved;
import com.hls.training.api.TrainingCannotAttend;
import com.hls.training.calendar.internal.GroupResolver.Refused;
import com.hls.training.calendar.internal.GroupResolver.Resolved;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expected attendance (spec 024 US3): assign people or a group to a session, remove them before it is held, and let an
 * assigned person say they cannot attend. A group is a snapshot of the day it is assigned. One row per person per session,
 * so assigning twice (one by one and through a group) never makes a second obligation. Callers have checked the grant.
 */
@Service("trainingAssignmentService")
public class AssignmentService {

    static final String ENTITY = "TRAINING_ASSIGNMENT";
    private static final int NOTE_MAX = 300;

    /** Who to assign: picked people ({@code personKind} and {@code ids}) or a group ({@code groupKind} and {@code groupRef}). */
    public record AssignBody(String personKind, List<UUID> ids, String groupKind, UUID groupRef, Boolean confirm) {}

    public record RemoveBody(String reason) {}

    public record NoteBody(String note) {}

    public record PreviewPerson(String kind, UUID id, String name, String state, String reason) {}

    public record Preview(int newCount, int alreadyCount, int refusedCount, List<PreviewPerson> people) {}

    public record AssignResult(int assigned, int alreadyAssigned, int refused, List<PreviewPerson> refusedPeople) {}

    public record AssignmentRow(
            UUID id, String personKind, UUID personId, String name, String source, String groupKind, String status,
            Instant assignedAt, String cannotAttendNote) {}

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final GroupResolver groups;
    private final PeopleDirectory people;
    private final TrainingScope scopes;
    private final ChangeRecorder changes;
    private final ApplicationEventPublisher events;
    private final TrainingClock clock;

    AssignmentService(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            GroupResolver groups,
            PeopleDirectory people,
            TrainingScope scopes,
            ChangeRecorder changes,
            ApplicationEventPublisher events,
            TrainingClock clock) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.registrations = registrations;
        this.groups = groups;
        this.people = people;
        this.scopes = scopes;
        this.changes = changes;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ preview and assign

    @Transactional(readOnly = true)
    public Preview preview(UUID sessionId, AssignBody body) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        Resolved resolved = resolve(body, session);
        Set<String> already = requiredKeys(sessionId);
        return preview(resolved, already);
    }

    @Transactional
    public AssignResult assign(Caller caller, UUID sessionId, AssignBody body) {
        if (!Boolean.TRUE.equals(body.confirm())) {
            throw new InvalidInputException("Confirm the assignment after checking the preview.");
        }
        TrainingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        if (!SessionRules.takesPeople(session.status)) {
            throw new ConflictException("People can only be assigned to a published session.");
        }
        Resolved resolved = resolve(body, session);
        boolean group = body.groupKind() != null && !body.groupKind().isBlank();
        return write(caller.userId(), session, resolved, group ? "GROUP" : "INDIVIDUAL", group ? body.groupKind() : null, group ? body.groupRef() : null);
    }

    /** Assigns a pattern's default group to a freshly generated session (no caller; the pattern's creator is recorded). */
    public AssignResult assignGroupAtGeneration(TrainingSession session, UUID by, String groupKind, UUID groupRef) {
        Resolved resolved = groups.group(groupKind, groupRef, session.sessionDate);
        return write(by, session, resolved, "GROUP", groupKind, groupRef);
    }

    private AssignResult write(UUID by, TrainingSession session, Resolved resolved, String source, String groupKind, UUID groupRef) {
        Instant now = clock.instant();
        List<Assignment> existing = assignments.findBySessionId(session.id);
        Map<String, Assignment> byKey = new java.util.HashMap<>();
        existing.forEach(a -> byKey.put(key(a.personKind, a.personId), a));
        int added = 0;
        int already = 0;
        Set<UUID> newTeachers = new HashSet<>();
        Set<UUID> newManagers = new HashSet<>();
        List<Assignment> toSave = new ArrayList<>();
        for (UUID id : resolved.teacherIds()) {
            added += place(byKey, toSave, newTeachers, session.id, Assignment.TEACHER, id, by, now, source, groupKind, groupRef) ? 1 : 0;
        }
        for (UUID id : resolved.managerIds()) {
            added += place(byKey, toSave, newManagers, session.id, Assignment.MANAGER, id, by, now, source, groupKind, groupRef) ? 1 : 0;
        }
        already = resolved.size() - added;
        assignments.saveAll(toSave);
        assignments.flush();
        if (added > 0) {
            changes.recordLifecycle(by, ENTITY, session.id, "assigned", added + " people (" + source + ")");
            Set<UUID> users = people.userIds(newTeachers, newManagers);
            if (!users.isEmpty()) {
                events.publishEvent(new TrainingAssigned(session.id, users, session.sessionDate, session.topic));
            }
        }
        List<PreviewPerson> refused = resolved.refused().stream().map(AssignmentService::refusedPerson).toList();
        return new AssignResult(added, already, refused.size(), refused);
    }

    /** Creates or revives one obligation; returns true when the person is newly required. */
    private boolean place(
            Map<String, Assignment> byKey, List<Assignment> toSave, Set<UUID> fresh, UUID sessionId, String kind, UUID personId,
            UUID by, Instant now, String source, String groupKind, UUID groupRef) {
        Assignment row = byKey.get(key(kind, personId));
        if (row != null && row.isRequired()) {
            return false;
        }
        if (row == null) {
            row = new Assignment(sessionId, kind, personId, by, now);
            byKey.put(key(kind, personId), row);
        } else {
            row.status = Assignment.REQUIRED;
            row.removedBy = null;
            row.removedAt = null;
            row.removedReason = null;
            row.assignedBy = by;
            row.assignedAt = now;
        }
        row.source = source;
        row.groupKind = groupKind;
        row.groupRef = groupRef;
        toSave.add(row);
        fresh.add(personId);
        return true;
    }

    // ------------------------------------------------------------------ remove

    @Transactional
    public void remove(Caller caller, UUID sessionId, UUID assignmentId, RemoveBody body) {
        TrainingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        Assignment row = assignments.findById(assignmentId)
                .filter(a -> a.sessionId.equals(sessionId))
                .orElseThrow(() -> new NotFoundException("Assignment not found."));
        if (TrainingSession.HELD.equals(session.status)) {
            throw new ConflictException("The session has been held, so people can no longer be removed from it.");
        }
        if (!row.isRequired()) {
            throw new ConflictException("This person is already removed.");
        }
        String reason = SessionRules.required("A reason", body == null ? null : body.reason(), SessionRules.REASON_MAX);
        markRemoved(row, caller.userId(), reason);
        assignments.saveAndFlush(row);
        changes.record(caller.userId(), ENTITY, row.id, "status", Assignment.REQUIRED, Assignment.REMOVED);
        notifyRemoved(session, row);
    }

    /** Flips a row to REMOVED (also used by the exit clean-up). */
    void markRemoved(Assignment row, UUID by, String reason) {
        row.status = Assignment.REMOVED;
        row.removedBy = by;
        row.removedAt = clock.instant();
        row.removedReason = reason;
    }

    void notifyRemoved(TrainingSession session, Assignment row) {
        Set<UUID> users = Assignment.MANAGER.equals(row.personKind)
                ? people.userIds(Set.of(), Set.of(row.personId))
                : people.userIds(Set.of(row.personId), Set.of());
        if (!users.isEmpty()) {
            events.publishEvent(new TrainingAssignmentRemoved(session.id, users, session.sessionDate, session.topic));
        }
    }

    // ------------------------------------------------------------------ cannot attend and roster

    /** The assigned person tells the Admin and Director they cannot attend. Nothing about the assignment changes. */
    @Transactional
    public void cannotAttend(Caller caller, UUID sessionId, NoteBody body) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        Scope scope = scopes.of(caller);
        String kind = scope.ownManagerId() != null ? Assignment.MANAGER : Assignment.TEACHER;
        UUID personId = scope.ownManagerId() != null ? scope.ownManagerId() : scope.ownTeacherId();
        Assignment row = personId == null
                ? null
                : assignments.findBySessionIdAndPersonKindAndPersonId(sessionId, kind, personId).filter(Assignment::isRequired).orElse(null);
        if (row == null) {
            throw new NotFoundException("You are not assigned to this session.");
        }
        if (session.isFinal() || TrainingSession.SKIPPED.equals(session.status)) {
            throw new ConflictException("This session is no longer open.");
        }
        String note = SessionRules.required("A note", body == null ? null : body.note(), NOTE_MAX);
        row.cannotAttendNote = note;
        assignments.saveAndFlush(row);
        changes.record(caller.userId(), ENTITY, row.id, "cannotAttendNote", null, note);
        events.publishEvent(new TrainingCannotAttend(sessionId, people.nameOf(kind, personId), note));
    }

    /** The obligations of a session; a Manager sees their own Teachers and themselves. */
    @Transactional(readOnly = true)
    public List<AssignmentRow> roster(Caller caller, UUID sessionId) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        if (!caller.orgWide() && TrainingSession.DRAFT.equals(session.status)) {
            throw new NotFoundException("Session not found.");
        }
        Scope scope = scopes.of(caller);
        List<Assignment> rows = assignments.findBySessionId(sessionId).stream().filter(a -> scope.allows(a.personKind, a.personId)).toList();
        Map<UUID, Person> names = people.people(
                rows.stream().filter(a -> Assignment.TEACHER.equals(a.personKind)).map(a -> a.personId).toList(),
                rows.stream().filter(a -> Assignment.MANAGER.equals(a.personKind)).map(a -> a.personId).toList());
        return rows.stream()
                .map(a -> new AssignmentRow(
                        a.id, a.personKind, a.personId, names.containsKey(a.personId) ? names.get(a.personId).name() : "Unknown",
                        a.source, a.groupKind, a.status, a.assignedAt, a.cannotAttendNote))
                .sorted(Comparator.comparing(AssignmentRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private Resolved resolve(AssignBody body, TrainingSession session) {
        if (body == null) {
            throw new InvalidInputException("Choose who to assign.");
        }
        if (body.groupKind() != null && !body.groupKind().isBlank()) {
            return groups.group(body.groupKind(), body.groupRef(), clock.today().isAfter(session.sessionDate) ? session.sessionDate : clock.today());
        }
        return groups.individuals(body.personKind(), body.ids());
    }

    private Set<String> requiredKeys(UUID sessionId) {
        Set<String> keys = new HashSet<>();
        assignments.findBySessionIdAndStatus(sessionId, Assignment.REQUIRED).forEach(a -> keys.add(key(a.personKind, a.personId)));
        return keys;
    }

    private Preview preview(Resolved resolved, Set<String> already) {
        List<PreviewPerson> out = new ArrayList<>();
        Map<UUID, Person> names = people.people(resolved.teacherIds(), resolved.managerIds());
        int fresh = 0;
        int dup = 0;
        for (UUID id : resolved.teacherIds()) {
            boolean has = already.contains(key(Assignment.TEACHER, id));
            out.add(new PreviewPerson(Assignment.TEACHER, id, nameOf(names, id), has ? "ALREADY" : "NEW", null));
            if (has) dup++; else fresh++;
        }
        for (UUID id : resolved.managerIds()) {
            boolean has = already.contains(key(Assignment.MANAGER, id));
            out.add(new PreviewPerson(Assignment.MANAGER, id, nameOf(names, id), has ? "ALREADY" : "NEW", null));
            if (has) dup++; else fresh++;
        }
        resolved.refused().forEach(r -> out.add(refusedPerson(r)));
        return new Preview(fresh, dup, resolved.refused().size(), out);
    }

    private static PreviewPerson refusedPerson(Refused r) {
        return new PreviewPerson(r.kind(), r.id(), r.name(), "REFUSED", r.reason());
    }

    private static String nameOf(Map<UUID, Person> names, UUID id) {
        return names.containsKey(id) ? names.get(id).name() : "Unknown";
    }

    private static String key(String kind, UUID id) {
        return kind + ":" + id;
    }
}
