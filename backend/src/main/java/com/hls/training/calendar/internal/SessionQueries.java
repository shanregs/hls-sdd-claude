package com.hls.training.calendar.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.training.api.TrainingSettings;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads sessions for the calendar and the detail page (spec 024 US1): what each caller may see, the counts, the buttons
 * (a server-sent {@code actions} list), the holiday notice and the derived "attendance not recorded" flag.
 */
@Service("trainingSessionQueries")
public class SessionQueries {

    /** Expected versus attended for one session (FR-015). {@code rate} is attended assigned people over assigned people. */
    public record Counts(
            int expected,
            int assigned,
            int registeredOnly,
            int attended,
            int absent,
            int excused,
            int walkIns,
            int noShows,
            int onLeave,
            BigDecimal rate) {}

    public record EarlierDate(String date, Instant changedAt, String reason) {}

    public record HistoryLine(String field, String before, String after, String reason, Instant changedAt, UUID changedBy) {}

    /** What the caller is to this session: {@code REQUIRED}, {@code REGISTERED} or null, and whether they could register. */
    public record My(String status, boolean open) {}

    public record SessionView(
            UUID id,
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
            String status,
            UUID patternId,
            LocalDate patternMonth,
            boolean handEdited,
            String cancelReason,
            String skipReason,
            Long version,
            int registered,
            Integer seatsLeft,
            Counts counts,
            List<String> actions,
            boolean holidayNotice,
            boolean attendanceNotRecorded,
            Integer groupNotFullyAssigned,
            List<EarlierDate> earlierDates,
            List<HistoryLine> history,
            My my) {}

    private final TrainingSessionRepository sessions;
    private final SessionHistoryRepository history;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final AttendanceEntryRepository entries;
    private final TrainingScope scopes;
    private final WorkingCalendar calendar;
    private final TrainingSettings settings;
    private final TrainingClock clock;
    private final GroupGaps groupGaps;

    SessionQueries(
            TrainingSessionRepository sessions,
            SessionHistoryRepository history,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            AttendanceEntryRepository entries,
            TrainingScope scopes,
            WorkingCalendar calendar,
            TrainingSettings settings,
            TrainingClock clock,
            GroupGaps groupGaps) {
        this.sessions = sessions;
        this.history = history;
        this.assignments = assignments;
        this.registrations = registrations;
        this.entries = entries;
        this.scopes = scopes;
        this.calendar = calendar;
        this.settings = settings;
        this.clock = clock;
        this.groupGaps = groupGaps;
    }

    // ------------------------------------------------------------------ list and detail

    @Transactional(readOnly = true)
    public List<SessionView> list(
            Caller caller, LocalDate from, LocalDate to, String type, String status, String trainer, UUID personId) {
        LocalDate start = from != null ? from : clock.today().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.plusMonths(1).minusDays(1);
        if (end.isBefore(start)) {
            throw new InvalidInputException("The end date must not be before the start date.");
        }
        if (personId != null && !caller.orgWide()) {
            throw new ForbiddenFieldException("Only an Admin or Director can filter by person.");
        }
        Scope scope = scopes.of(caller);
        List<TrainingSession> found = sessions.findBySessionDateBetweenOrderBySessionDateAscStartsAtAsc(start, end).stream()
                .filter(s -> type == null || type.isBlank() || type.equalsIgnoreCase(s.sessionType))
                .filter(s -> status == null || status.isBlank() || status.equalsIgnoreCase(s.status))
                .filter(s -> trainer == null
                        || trainer.isBlank()
                        || (s.trainerName != null && s.trainerName.toLowerCase(Locale.ROOT).contains(trainer.trim().toLowerCase(Locale.ROOT))))
                .toList();
        Links links = Links.load(found, assignments, registrations, entries);
        List<TrainingSession> visible = found.stream().filter(s -> visible(caller, scope, s, links)).toList();
        if (personId != null) {
            visible = visible.stream().filter(s -> links.involves(s.id, personId)).toList();
        }
        Set<LocalDate> working = new HashSet<>(calendar.workingDays(null, start, end));
        return visible.stream().map(s -> build(caller, scope, s, links, working, false)).toList();
    }

    @Transactional(readOnly = true)
    public SessionView detail(Caller caller, UUID id) {
        TrainingSession session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Session not found."));
        Scope scope = scopes.of(caller);
        Links links = Links.load(List.of(session), assignments, registrations, entries);
        if (!visible(caller, scope, session, links)) {
            throw new NotFoundException("Session not found.");
        }
        Set<LocalDate> working = new HashSet<>(calendar.workingDays(null, session.sessionDate, session.sessionDate));
        return build(caller, scope, session, links, working, true);
    }

    /** The session, or "not found" when the caller may not see it. Used by the writing services. */
    @Transactional(readOnly = true)
    public TrainingSession requireVisible(Caller caller, UUID id) {
        TrainingSession session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Session not found."));
        Links links = Links.load(List.of(session), assignments, registrations, entries);
        if (!visible(caller, scopes.of(caller), session, links)) {
            throw new NotFoundException("Session not found.");
        }
        return session;
    }

    // ------------------------------------------------------------------ visibility

    /** Admin and Director see everything; a Manager or Teacher sees a shown session that includes them or their Teachers. */
    private boolean visible(Caller caller, Scope scope, TrainingSession s, Links links) {
        if (caller.orgWide()) {
            return true;
        }
        if (!TrainingSession.PUBLISHED.equals(s.status) && !TrainingSession.HELD.equals(s.status) && !TrainingSession.CANCELLED.equals(s.status)) {
            return false;
        }
        if (caller.isTeacher() && TrainingSession.WEEKEND.equals(s.sessionType) && TrainingSession.PUBLISHED.equals(s.status)) {
            return true;
        }
        return links.assignments(s.id).stream().anyMatch(a -> Assignment.REQUIRED.equals(a.status) && scope.allows(a.personKind, a.personId))
                || links.registrations(s.id).stream().anyMatch(r -> scope.allowsTeacher(r.teacherId))
                || links.entries(s.id).stream().anyMatch(e -> scope.allows(e.personKind, e.personId));
    }

    // ------------------------------------------------------------------ building a view

    private SessionView build(Caller caller, Scope scope, TrainingSession s, Links links, Set<LocalDate> working, boolean detail) {
        List<Assignment> assigned = links.assignments(s.id);
        List<Registration> registered = links.registrations(s.id);
        List<AttendanceEntry> recorded = links.entries(s.id);
        int registeredCount = (int) registered.stream().filter(Registration::isRegistered).count();
        Integer seatsLeft = s.seatLimit == null ? null : Math.max(0, s.seatLimit - registeredCount);
        boolean staff = !caller.isTeacher();
        Counts counts = staff ? counts(scope, assigned, registered, recorded) : null;
        boolean notRecorded = staff
                && TrainingSession.PUBLISHED.equals(s.status)
                && s.sessionDate.plusDays(settings.attendanceDueDays()).isBefore(clock.today());
        Integer gap = staff && detail && !s.isFinal() && caller.orgWide() ? groupGaps.notFullyAssigned(s, assigned) : null;
        List<EarlierDate> earlier = List.of();
        List<HistoryLine> lines = null;
        if (detail) {
            List<SessionHistory> rows = history.findBySessionIdOrderByChangedAtAsc(s.id);
            earlier = rows.stream().filter(h -> "date".equals(h.field)).map(h -> new EarlierDate(h.beforeValue, h.changedAt, h.reason)).toList();
            lines = rows.stream().map(h -> new HistoryLine(h.field, h.beforeValue, h.afterValue, h.reason, h.changedAt, h.changedBy)).toList();
        }
        return new SessionView(
                s.id,
                s.sessionType,
                s.topic,
                s.sessionDate,
                s.startsAt,
                s.endsAt,
                s.trainerName,
                s.trainerContact,
                s.venueKind,
                s.venue,
                s.seatLimit,
                s.description,
                s.status,
                s.patternId,
                s.patternMonth,
                s.handEdited,
                s.cancelReason,
                s.skipReason,
                s.version,
                registeredCount,
                seatsLeft,
                counts,
                actions(caller, scope, s, assigned, registered, recorded, seatsLeft),
                !working.contains(s.sessionDate),
                notRecorded,
                gap,
                earlier,
                lines,
                my(scope, s, assigned, registered, seatsLeft));
    }

    private My my(Scope scope, TrainingSession s, List<Assignment> assigned, List<Registration> registered, Integer seatsLeft) {
        String status = null;
        if (assigned.stream().anyMatch(a -> a.isRequired() && scope.allows(a.personKind, a.personId) && isOwn(scope, a))) {
            status = "REQUIRED";
        } else if (scope.ownTeacherId() != null
                && registered.stream().anyMatch(r -> r.isRegistered() && r.teacherId.equals(scope.ownTeacherId()))) {
            status = "REGISTERED";
        }
        boolean open = scope.ownTeacherId() != null
                && TrainingSession.WEEKEND.equals(s.sessionType)
                && TrainingSession.PUBLISHED.equals(s.status)
                && !clock.started(s.sessionDate, s.startsAt)
                && (seatsLeft == null || seatsLeft > 0);
        return new My(status, open);
    }

    private static boolean isOwn(Scope scope, Assignment a) {
        return Assignment.MANAGER.equals(a.personKind) ? a.personId.equals(scope.ownManagerId()) : a.personId.equals(scope.ownTeacherId());
    }

    /** The buttons the caller sees on this session, from their grants and the session's state. */
    private List<String> actions(
            Caller caller,
            Scope scope,
            TrainingSession s,
            List<Assignment> assigned,
            List<Registration> registered,
            List<AttendanceEntry> recorded,
            Integer seatsLeft) {
        List<String> out = new ArrayList<>();
        Caller.Grants g = caller.grants();
        boolean started = clock.started(s.sessionDate, s.startsAt);
        if (g.edit() && SessionRules.editable(s.status)) {
            out.add("EDIT");
        }
        if (g.edit() && SessionRules.publishable(s.status)) {
            out.add("PUBLISH");
        }
        if (g.edit() && SessionRules.movable(s.status)) {
            out.add("MOVE");
        }
        if (g.edit() && SessionRules.cancellable(s.status)) {
            out.add("CANCEL");
        }
        if (g.delete() && SessionRules.deletable(s.status)) {
            out.add("DELETE");
        }
        if (g.edit() && SessionRules.takesPeople(s.status)) {
            out.add("ASSIGN");
        }
        if (g.create() && TrainingSession.WEEKEND.equals(s.sessionType) && SessionRules.takesPeople(s.status) && !started) {
            out.add("REGISTER_TEACHER");
        }
        if (g.attendanceEdit()
                && (TrainingSession.PUBLISHED.equals(s.status) || TrainingSession.HELD.equals(s.status))
                && !s.sessionDate.isAfter(clock.today())) {
            out.add("RECORD_ATTENDANCE");
        }
        if (scope.ownTeacherId() != null && g.registrationCreate() && TrainingSession.WEEKEND.equals(s.sessionType)
                && SessionRules.takesPeople(s.status) && !started) {
            boolean mine = registered.stream().anyMatch(r -> r.isRegistered() && r.teacherId.equals(scope.ownTeacherId()));
            if (mine) {
                if (g.registrationEdit()) {
                    out.add("CANCEL_REGISTRATION");
                }
            } else if (seatsLeft == null || seatsLeft > 0) {
                out.add("REGISTER");
            }
        }
        boolean required = assigned.stream().anyMatch(a -> a.isRequired() && isOwn(scope, a));
        if (required && !s.isFinal() && !TrainingSession.SKIPPED.equals(s.status)) {
            out.add("CANNOT_ATTEND");
        }
        return out;
    }

    // ------------------------------------------------------------------ counts

    /** The counts over the people the scope reaches (all of them for Admin and Director). */
    Counts counts(Scope scope, List<Assignment> assigned, List<Registration> registered, List<AttendanceEntry> recorded) {
        Set<UUID> assignedTeachers = new HashSet<>();
        int assignedCount = 0;
        for (Assignment a : assigned) {
            if (a.isRequired() && scope.allows(a.personKind, a.personId)) {
                assignedCount++;
                if (Assignment.TEACHER.equals(a.personKind)) {
                    assignedTeachers.add(a.personId);
                }
            }
        }
        int registeredOnly = (int) registered.stream()
                .filter(r -> r.isRegistered() && scope.allowsTeacher(r.teacherId) && !assignedTeachers.contains(r.teacherId))
                .count();
        int attended = 0;
        int absent = 0;
        int excused = 0;
        int walkIns = 0;
        int noShows = 0;
        int onLeave = 0;
        Set<String> assignedKeys = assigned.stream()
                .filter(Assignment::isRequired)
                .map(a -> a.personKind + ":" + a.personId)
                .collect(Collectors.toSet());
        int assignedPresent = 0;
        for (AttendanceEntry e : recorded) {
            if (!scope.allows(e.personKind, e.personId)) {
                continue;
            }
            if ("WALK_IN".equals(e.basis)) {
                walkIns++;
                continue;
            }
            switch (e.outcome) {
                case AttendanceEntry.PRESENT -> {
                    attended++;
                    if (assignedKeys.contains(e.personKind + ":" + e.personId)) {
                        assignedPresent++;
                    }
                }
                case AttendanceEntry.ABSENT -> {
                    absent++;
                    if ("REGISTERED".equals(e.basis)) {
                        noShows++;
                    }
                }
                case AttendanceEntry.EXCUSED -> excused++;
                default -> {
                }
            }
            if (e.onLeave) {
                onLeave++;
            }
        }
        BigDecimal rate = assignedCount == 0
                ? null
                : BigDecimal.valueOf(assignedPresent).divide(BigDecimal.valueOf(assignedCount), 2, RoundingMode.HALF_UP);
        return new Counts(assignedCount + registeredOnly, assignedCount, registeredOnly, attended, absent, excused, walkIns, noShows, onLeave, rate);
    }

    // ------------------------------------------------------------------ bulk links

    /** The assignments, registrations and entries of a set of sessions, loaded with three queries. */
    record Links(
            Map<UUID, List<Assignment>> assignmentsBySession,
            Map<UUID, List<Registration>> registrationsBySession,
            Map<UUID, List<AttendanceEntry>> entriesBySession) {

        static Links load(
                Collection<TrainingSession> sessions,
                AssignmentRepository assignments,
                RegistrationRepository registrations,
                AttendanceEntryRepository entries) {
            List<UUID> ids = sessions.stream().map(s -> s.id).toList();
            if (ids.isEmpty()) {
                return new Links(Map.of(), Map.of(), Map.of());
            }
            return new Links(
                    assignments.findBySessionIdIn(ids).stream().collect(Collectors.groupingBy(a -> a.sessionId)),
                    registrations.findBySessionIdIn(ids).stream().collect(Collectors.groupingBy(r -> r.sessionId)),
                    entries.findBySessionIdIn(ids).stream().collect(Collectors.groupingBy(e -> e.sessionId)));
        }

        List<Assignment> assignments(UUID sessionId) {
            return assignmentsBySession.getOrDefault(sessionId, List.of());
        }

        List<Registration> registrations(UUID sessionId) {
            return registrationsBySession.getOrDefault(sessionId, List.of());
        }

        List<AttendanceEntry> entries(UUID sessionId) {
            return entriesBySession.getOrDefault(sessionId, List.of());
        }

        /** True when the person (a Teacher or Manager id) is assigned, registered or recorded at the session. */
        boolean involves(UUID sessionId, UUID personId) {
            return assignments(sessionId).stream().anyMatch(a -> a.isRequired() && a.personId.equals(personId))
                    || registrations(sessionId).stream().anyMatch(r -> r.isRegistered() && r.teacherId.equals(personId))
                    || entries(sessionId).stream().anyMatch(e -> e.personId.equals(personId));
        }
    }

    /** Sorts newest first; shared by the history reads. */
    static final Comparator<SessionView> BY_DATE = Comparator.comparing(SessionView::date).thenComparing(SessionView::startsAt);

    @SuppressWarnings("unused")
    private static <T> Optional<T> first(List<T> list) {
        return list.stream().findFirst();
    }

    @SuppressWarnings("unused")
    private static Map<UUID, UUID> unused() {
        return new HashMap<>();
    }
}
