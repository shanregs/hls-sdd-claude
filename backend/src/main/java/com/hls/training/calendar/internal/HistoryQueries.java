package com.hls.training.calendar.internal;

import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.training.api.InductionBatches;
import com.hls.training.api.InductionBatches.InductionRecord;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Training completion history of one person (spec 024 US6): every session they were required at, registered for or walked
 * into, once each, newest first, with the induction record of a Teacher and the yearly attended-versus-expected figure
 * (research.md section 10). Staff see internal remarks and exceptions; the person never does.
 */
@Service("trainingHistoryQueries")
public class HistoryQueries {

    public record Entry(
            String type,
            UUID sessionId,
            String sessionType,
            String topic,
            LocalDate date,
            String sessionStatus,
            String role,
            String outcome,
            BigDecimal dayValue,
            String reason,
            String certificateNote,
            String internalRemark,
            String exceptionStatus,
            boolean onLeave) {}

    public record InductionEntry(String type, UUID batchId, String batchName, LocalDate startsOn, LocalDate endsOn, String result, java.time.Instant signedAt) {}

    public record Yearly(BigDecimal expected, BigDecimal attended, int expectedSessions, int attendedSessions) {}

    public record PersonRef(String kind, UUID id, String name) {}

    public record History(PersonRef person, int year, List<Entry> entries, List<InductionEntry> induction, Yearly yearly) {}

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final AttendanceEntryRepository entries;
    private final ExceptionRepository exceptions;
    private final PeopleDirectory people;
    private final TrainingScope scopes;
    private final InductionBatches induction;
    private final TrainingClock clock;

    HistoryQueries(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            AttendanceEntryRepository entries,
            ExceptionRepository exceptions,
            PeopleDirectory people,
            TrainingScope scopes,
            InductionBatches induction,
            TrainingClock clock) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.registrations = registrations;
        this.entries = entries;
        this.exceptions = exceptions;
        this.people = people;
        this.scopes = scopes;
        this.induction = induction;
        this.clock = clock;
    }

    /** The caller's own history (a Teacher, or a Manager's own record). */
    @Transactional(readOnly = true)
    public History mine(Caller caller, Integer year) {
        Scope scope = scopes.of(caller);
        if (scope.ownTeacherId() != null) {
            return of(caller, Assignment.TEACHER, scope.ownTeacherId(), year);
        }
        if (scope.ownManagerId() != null) {
            return of(caller, Assignment.MANAGER, scope.ownManagerId(), year);
        }
        throw new NotFoundException("Person not found.");
    }

    @Transactional(readOnly = true)
    public History of(Caller caller, String kind, UUID id, Integer yearValue) {
        if (!Assignment.TEACHER.equals(kind) && !Assignment.MANAGER.equals(kind)) {
            throw new InvalidInputException("The person must be a Teacher or a Manager.");
        }
        Scope scope = scopes.of(caller);
        scope.require(kind, id);
        Person person = people.person(kind, id);
        if (person == null) {
            throw new NotFoundException("Person not found.");
        }
        boolean self = caller.isTeacher() || (scope.ownManagerId() != null && scope.ownManagerId().equals(id) && !caller.orgWide());
        int year = yearValue != null ? yearValue : clock.today().getYear();
        LocalDate today = clock.today();

        List<Assignment> myAssignments = assignments.findByPersonKindAndPersonId(kind, id).stream().filter(Assignment::isRequired).toList();
        List<Registration> myRegistrations = Assignment.TEACHER.equals(kind)
                ? registrations.findByTeacherId(id).stream().filter(Registration::isRegistered).toList()
                : List.of();
        Map<UUID, AttendanceEntry> myEntries = entries.findByPersonKindAndPersonId(kind, id).stream()
                .collect(Collectors.toMap(e -> e.sessionId, e -> e, (a, b) -> a));
        Map<UUID, TrainingException> myExceptions = exceptions.findByPersonKindAndPersonId(kind, id).stream()
                .collect(Collectors.toMap(e -> e.sessionId, e -> e, (a, b) -> a));

        Set<UUID> sessionIds = new HashSet<>();
        myAssignments.forEach(a -> sessionIds.add(a.sessionId));
        myRegistrations.forEach(r -> sessionIds.add(r.sessionId));
        sessionIds.addAll(myEntries.keySet());
        Set<UUID> requiredIds = myAssignments.stream().map(a -> a.sessionId).collect(Collectors.toSet());
        Set<UUID> registeredIds = myRegistrations.stream().map(r -> r.sessionId).collect(Collectors.toSet());

        List<Entry> out = new ArrayList<>();
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal attended = BigDecimal.ZERO;
        int expectedSessions = 0;
        int attendedSessions = 0;
        for (TrainingSession s : sessions.findByIdIn(sessionIds)) {
            if (TrainingSession.DRAFT.equals(s.status) || TrainingSession.SKIPPED.equals(s.status) || s.sessionDate.getYear() != year) {
                continue;
            }
            AttendanceEntry e = myEntries.get(s.id);
            TrainingException x = myExceptions.get(s.id);
            String role = requiredIds.contains(s.id) ? "REQUIRED" : registeredIds.contains(s.id) ? "REGISTERED" : e != null ? e.basis : "REGISTERED";
            boolean counts = requiredIds.contains(s.id)
                    && !TrainingSession.CANCELLED.equals(s.status)
                    && (TrainingSession.HELD.equals(s.status) || s.sessionDate.isBefore(today));
            if (counts) {
                expected = expected.add(BigDecimal.ONE);
                expectedSessions++;
                if (e != null && AttendanceEntry.PRESENT.equals(e.outcome) && e.dayValue != null) {
                    attended = attended.add(e.dayValue);
                    attendedSessions++;
                }
            }
            out.add(new Entry(
                    "SESSION", s.id, s.sessionType, s.topic, s.sessionDate, s.status, role,
                    e == null ? null : e.outcome, e == null ? null : e.dayValue, e == null ? null : e.reason,
                    e == null ? null : e.certificateNote,
                    self || e == null ? null : e.internalRemark,
                    self || x == null || TrainingException.WITHDRAWN.equals(x.status) ? null : x.status,
                    e != null && e.onLeave));
        }
        out.sort(Comparator.comparing(Entry::date).reversed().thenComparing(Entry::topic));

        List<InductionEntry> inductionEntries = Assignment.TEACHER.equals(kind)
                ? induction.recordsOf(id).stream()
                        .map(r -> new InductionEntry("INDUCTION", r.batchId(), r.batchName(), r.startsOn(), r.endsOn(), r.result(), r.signedAt()))
                        .toList()
                : List.of();
        return new History(
                new PersonRef(kind, id, person.name()), year, out, inductionEntries,
                new Yearly(expected, attended, expectedSessions, attendedSessions));
    }

    /** CSV of the history (staff export). */
    public String csv(History h) {
        StringBuilder out = new StringBuilder("Date,Topic,Type,Status,Role,Outcome,Day,Reason\n");
        for (Entry e : h.entries()) {
            out.append(e.date()).append(',').append(csv(e.topic())).append(',').append(e.sessionType()).append(',').append(e.sessionStatus())
                    .append(',').append(e.role()).append(',').append(e.outcome() == null ? "" : e.outcome()).append(',')
                    .append(e.dayValue() == null ? "" : e.dayValue().toPlainString()).append(',').append(csv(e.reason())).append('\n');
        }
        for (InductionEntry i : h.induction()) {
            out.append(i.startsOn()).append(',').append(csv(i.batchName())).append(",INDUCTION,,,").append(i.result() == null ? "" : i.result()).append(",,\n");
        }
        return out.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
