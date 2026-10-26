package com.hls.training.calendar.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.training.calendar.internal.AttendanceBridge.Outcome;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import com.hls.training.calendar.internal.SessionQueries.Counts;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records who attended a session (spec 024 US4): one outcome per person, saved for many people in one transaction with a
 * result per row. A Teacher who was present gets a training day in attendance through {@link AttendanceBridge}; a row that
 * 008 refuses (a locked month) saves nothing while the other rows are kept. The first save marks the session as held.
 * Callers have checked the {@code TRAINING_ATTENDANCE} grant; scope is decided here.
 */
@Service("trainingAttendanceRecordService")
public class AttendanceRecordService {

    static final String ENTITY = "TRAINING_ATTENDANCE";
    static final String ON_LEAVE_REASON = "On leave";
    static final String WRITTEN = "WRITTEN";
    static final String CLASH = "CLASH";
    static final String PENDING = "PENDING";
    static final String NOT_NEEDED = "NOT_NEEDED";
    static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    public record RowInput(
            String personKind,
            UUID personId,
            String outcome,
            BigDecimal dayValue,
            String reason,
            String certificateNote,
            String internalRemark,
            Long version) {}

    public record SaveBody(List<RowInput> rows) {}

    public record WalkInBody(String personKind, UUID personId, BigDecimal dayValue) {}

    public record HistoryLine(String action, String outcome, BigDecimal dayValue, String reason, Instant changedAt) {}

    public record RosterRow(
            UUID entryId,
            String personKind,
            UUID personId,
            String name,
            String basis,
            boolean assigned,
            boolean registered,
            String outcome,
            BigDecimal dayValue,
            String reason,
            String certificateNote,
            String internalRemark,
            boolean onLeave,
            String syncState,
            String clashNote,
            Long version,
            List<HistoryLine> history) {}

    public record RowResult(UUID personId, String personKind, String status, String code, String message) {}

    public record SaveResult(List<RowResult> results, Counts counts) {}

    private final TrainingSessionRepository sessions;
    private final AssignmentRepository assignments;
    private final RegistrationRepository registrations;
    private final AttendanceEntryRepository entries;
    private final AttendanceHistoryRepository entryHistory;
    private final SessionHistoryRepository sessionHistory;
    private final TrainingScope scopes;
    private final PeopleDirectory people;
    private final AttendanceBridge bridge;
    private final AttendanceReadApi attendance;
    private final ManagerAttendanceReadApi managerAttendance;
    private final SessionQueries queries;
    private final ChangeRecorder changes;
    private final TrainingClock clock;
    private final ExceptionService exceptionService;

    AttendanceRecordService(
            TrainingSessionRepository sessions,
            AssignmentRepository assignments,
            RegistrationRepository registrations,
            AttendanceEntryRepository entries,
            AttendanceHistoryRepository entryHistory,
            SessionHistoryRepository sessionHistory,
            TrainingScope scopes,
            PeopleDirectory people,
            AttendanceBridge bridge,
            AttendanceReadApi attendance,
            ManagerAttendanceReadApi managerAttendance,
            SessionQueries queries,
            ChangeRecorder changes,
            TrainingClock clock,
            ExceptionService exceptionService) {
        this.sessions = sessions;
        this.assignments = assignments;
        this.registrations = registrations;
        this.entries = entries;
        this.entryHistory = entryHistory;
        this.sessionHistory = sessionHistory;
        this.scopes = scopes;
        this.people = people;
        this.bridge = bridge;
        this.attendance = attendance;
        this.managerAttendance = managerAttendance;
        this.queries = queries;
        this.changes = changes;
        this.clock = clock;
        this.exceptionService = exceptionService;
    }

    // ------------------------------------------------------------------ roster

    @Transactional(readOnly = true)
    public List<RosterRow> roster(Caller caller, UUID sessionId) {
        TrainingSession session = sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        if (TrainingSession.DRAFT.equals(session.status)) {
            throw new NotFoundException("Session not found.");
        }
        Scope scope = scopes.of(caller);
        List<Assignment> assigned = assignments.findBySessionId(sessionId).stream().filter(Assignment::isRequired).toList();
        List<Registration> registered = registrations.findBySessionId(sessionId).stream().filter(Registration::isRegistered).toList();
        List<AttendanceEntry> recorded = entries.findBySessionId(sessionId);

        Map<String, RosterRow> rows = new HashMap<>();
        Map<String, AttendanceEntry> entryByKey = new HashMap<>();
        recorded.forEach(e -> entryByKey.put(key(e.personKind, e.personId), e));
        Map<UUID, List<HistoryLine>> history = historyOf(recorded);

        Set<UUID> teacherIds = new HashSet<>();
        Set<UUID> managerIds = new HashSet<>();
        List<Object[]> base = new ArrayList<>(); // kind, id, basis, assigned, registered
        for (Assignment a : assigned) {
            base.add(new Object[] {a.personKind, a.personId, "ASSIGNED", true, false});
        }
        Set<String> assignedKeys = assigned.stream().map(a -> key(a.personKind, a.personId)).collect(Collectors.toSet());
        for (Registration r : registered) {
            if (assignedKeys.contains(key(Assignment.TEACHER, r.teacherId))) {
                base.stream()
                        .filter(b -> b[0].equals(Assignment.TEACHER) && b[1].equals(r.teacherId))
                        .forEach(b -> b[4] = true);
            } else {
                base.add(new Object[] {Assignment.TEACHER, r.teacherId, "REGISTERED", false, true});
            }
        }
        Set<String> baseKeys = base.stream().map(b -> key((String) b[0], (UUID) b[1])).collect(Collectors.toSet());
        for (AttendanceEntry e : recorded) {
            if (!baseKeys.contains(key(e.personKind, e.personId))) {
                base.add(new Object[] {e.personKind, e.personId, e.basis, false, false});
            }
        }
        for (Object[] b : base) {
            (Assignment.MANAGER.equals(b[0]) ? managerIds : teacherIds).add((UUID) b[1]);
        }
        Map<UUID, Person> names = people.people(teacherIds, managerIds);
        Set<UUID> awayTeachers = teacherIds.isEmpty() ? Set.of() : attendance.teachersAwayOn(teacherIds, session.sessionDate);
        Set<UUID> awayManagers = managerIds.isEmpty() ? Set.of() : managerAttendance.managersOnLeaveOn(managerIds, session.sessionDate);

        for (Object[] b : base) {
            String kind = (String) b[0];
            UUID id = (UUID) b[1];
            if (!allowedOnRoster(caller, scope, kind, id)) {
                continue;
            }
            AttendanceEntry e = entryByKey.get(key(kind, id));
            boolean away = Assignment.MANAGER.equals(kind) ? awayManagers.contains(id) : awayTeachers.contains(id);
            String name = names.containsKey(id) ? names.get(id).name() : "Unknown";
            rows.put(key(kind, id), new RosterRow(
                    e == null ? null : e.id, kind, id, name, e != null ? e.basis : (String) b[2], (boolean) b[3], (boolean) b[4],
                    e == null ? null : e.outcome, e == null ? null : e.dayValue, e == null ? null : e.reason,
                    e == null ? null : e.certificateNote, e == null ? null : e.internalRemark,
                    e != null ? e.onLeave : away, e == null ? null : e.syncState, e == null ? null : e.clashNote,
                    e == null ? null : e.version, e == null ? List.of() : history.getOrDefault(e.id, List.of())));
        }
        return rows.values().stream().sorted(Comparator.comparing(RosterRow::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // ------------------------------------------------------------------ save

    @Transactional
    public SaveResult save(Caller caller, UUID sessionId, SaveBody body) {
        if (body == null || body.rows() == null || body.rows().isEmpty()) {
            throw new InvalidInputException("There is nothing to save.");
        }
        TrainingSession session = lockedOpenSession(sessionId);
        Context ctx = context(caller, session, body.rows().stream().map(r -> new Ref(r.personKind(), r.personId())).toList());
        List<RowResult> results = new ArrayList<>();
        boolean any = false;
        for (RowInput row : body.rows()) {
            RowResult result = saveRow(ctx, row, false);
            results.add(result);
            any |= !AttendanceBridge.REFUSED.equals(result.status());
        }
        if (any) {
            markHeld(caller, session);
        }
        return new SaveResult(results, countsFor(caller, session));
    }

    @Transactional
    public SaveResult walkIn(Caller caller, UUID sessionId, WalkInBody body) {
        if (body == null || body.personId() == null) {
            throw new InvalidInputException("Choose the person.");
        }
        TrainingSession session = lockedOpenSession(sessionId);
        Context ctx = context(caller, session, List.of(new Ref(body.personKind(), body.personId())));
        RowInput row = new RowInput(body.personKind(), body.personId(), AttendanceEntry.PRESENT, body.dayValue(), null, null, null, null);
        RowResult result = saveRow(ctx, row, true);
        if (!AttendanceBridge.REFUSED.equals(result.status())) {
            markHeld(caller, session);
        }
        return new SaveResult(List.of(result), countsFor(caller, session));
    }

    /** Retries the 008 write of a Teacher row whose earlier write clashed with a School mark that has since been removed. */
    @Transactional
    public SaveResult sync(Caller caller, UUID sessionId, UUID entryId) {
        TrainingSession session = lockedOpenSession(sessionId);
        AttendanceEntry entry = entries.findById(entryId).filter(e -> e.sessionId.equals(sessionId))
                .orElseThrow(() -> new NotFoundException("Attendance row not found."));
        Scope scope = scopes.of(caller);
        if (!scope.allows(entry.personKind, entry.personId) || !allowedOnRoster(caller, scope, entry.personKind, entry.personId)) {
            throw new NotFoundException("Attendance row not found.");
        }
        RowResult result;
        if (!Assignment.TEACHER.equals(entry.personKind) || !AttendanceEntry.PRESENT.equals(entry.outcome)
                || !(CLASH.equals(entry.syncState) || PENDING.equals(entry.syncState))) {
            result = new RowResult(entry.personId, entry.personKind, AttendanceBridge.SAVED, null, null);
        } else {
            result = applySync(caller, session, entry);
        }
        return new SaveResult(List.of(result), countsFor(caller, session));
    }

    /** Used by the reconcile job and the sync endpoint: writes the training day of a Present Teacher row. */
    RowResult applySync(Caller caller, TrainingSession session, AttendanceEntry entry) {
        Outcome out = bridge.markPresent(caller == null ? entry.recordedBy : caller.userId(), entry.personId, session.sessionDate, entry.dayValue);
        if (out.saved()) {
            entry.syncState = WRITTEN;
            entry.clashNote = null;
        } else if (out.clash()) {
            entry.syncState = CLASH;
            entry.clashNote = out.message();
        }
        entries.saveAndFlush(entry);
        return new RowResult(entry.personId, entry.personKind, out.status(), out.code(), out.message());
    }

    // ------------------------------------------------------------------ one row

    private RowResult saveRow(Context ctx, RowInput row, boolean walkIn) {
        String kind = row.personKind();
        UUID id = row.personId();
        if (id == null || (!Assignment.TEACHER.equals(kind) && !Assignment.MANAGER.equals(kind))) {
            return refused(row, "NOT_ON_ROSTER", "This person is not on the roster.");
        }
        if (!ctx.scope.allows(kind, id) || (Assignment.MANAGER.equals(kind) && !ctx.caller.orgWide())) {
            return refused(row, "NOT_IN_SCOPE", "You can only record attendance for the Teachers placed under you.");
        }
        String basis = ctx.basisOf(kind, id);
        if (basis == null && walkIn) {
            Person p = ctx.names.get(id);
            if (p == null || !p.active() && !"ON_LEAVE".equals(p.status())) {
                return refused(row, "NOT_ON_ROSTER", "This person cannot be recorded.");
            }
            basis = "WALK_IN";
        }
        if (basis == null) {
            return refused(row, "NOT_ON_ROSTER", "This person is not on the roster. Record them as a walk-in.");
        }
        String outcome = row.outcome();
        if (!AttendanceEntry.PRESENT.equals(outcome) && !AttendanceEntry.ABSENT.equals(outcome) && !AttendanceEntry.EXCUSED.equals(outcome)) {
            return refused(row, "OUTCOME", "Choose Present, Absent or Excused.");
        }
        boolean present = AttendanceEntry.PRESENT.equals(outcome);
        boolean onLeave = ctx.onLeave(kind, id);
        String reason = blank(row.reason()) ? null : row.reason().trim();
        if (!present && reason == null && onLeave && AttendanceEntry.ABSENT.equals(outcome)) {
            reason = ON_LEAVE_REASON;
        }
        if (!present && reason == null) {
            return refused(row, "REASON_REQUIRED", "Enter a reason for Absent or Excused.");
        }
        if (reason != null && reason.length() > SessionRules.REASON_MAX) {
            return refused(row, "REASON_REQUIRED", "The reason must be at most " + SessionRules.REASON_MAX + " characters.");
        }
        BigDecimal value = null;
        if (present) {
            value = row.dayValue() == null ? new BigDecimal("1.00") : row.dayValue().setScale(2, java.math.RoundingMode.HALF_UP);
            if (value.compareTo(new BigDecimal("1.00")) != 0 && value.compareTo(new BigDecimal("0.50")) != 0) {
                return refused(row, "DAY_VALUE", "A day is whole (1) or half (0.5).");
            }
        }
        String certificate = trimTo(row.certificateNote(), 300);
        String remark = trimTo(row.internalRemark(), SessionRules.REMARK_MAX);

        AttendanceEntry entry = ctx.entries.get(key(kind, id));
        if (entry != null && row.version() != null && !row.version().equals(entry.version)) {
            return refused(row, "STALE", "This row was changed by someone else. Reload and try again.");
        }
        boolean wasPresent = entry != null && AttendanceEntry.PRESENT.equals(entry.outcome);
        String previousSync = entry == null ? null : entry.syncState;
        boolean unchanged = entry != null
                && Objects.equals(entry.outcome, outcome)
                && (value == null ? entry.dayValue == null : entry.dayValue != null && entry.dayValue.compareTo(value) == 0)
                && Objects.equals(entry.reason, reason)
                && Objects.equals(entry.certificateNote, certificate)
                && Objects.equals(entry.internalRemark, remark);
        if (unchanged) {
            return new RowResult(id, kind, AttendanceBridge.SAVED, null, null);
        }

        // the 008 side first: a refusal leaves everything for this row untouched
        String syncState = NOT_APPLICABLE;
        String clashNote = null;
        Outcome sync = new Outcome(AttendanceBridge.SAVED, null, null);
        if (Assignment.TEACHER.equals(kind)) {
            syncState = NOT_NEEDED;
            if (present) {
                sync = bridge.markPresent(ctx.caller.userId(), id, ctx.session.sessionDate, value);
                if (sync.refused()) {
                    return refused(row, sync.code(), sync.message());
                }
                syncState = sync.clash() ? CLASH : WRITTEN;
                clashNote = sync.clash() ? sync.message() : null;
            } else if (wasPresent && (WRITTEN.equals(previousSync) || PENDING.equals(previousSync))) {
                Outcome cleared = bridge.clear(ctx.caller.userId(), id, ctx.session.sessionDate);
                if (cleared.refused()) {
                    return refused(row, cleared.code(), cleared.message());
                }
            }
        }

        Instant now = clock.instant();
        String before = entry == null ? null : entry.outcome + (entry.dayValue == null ? "" : " " + entry.dayValue.toPlainString());
        if (entry == null) {
            entry = new AttendanceEntry(ctx.session.id, kind, id, ctx.caller.userId(), now);
            entry.basis = basis;
        }
        entry.outcome = outcome;
        entry.dayValue = value;
        entry.reason = reason;
        entry.onLeave = onLeave;
        entry.certificateNote = certificate;
        entry.internalRemark = remark;
        entry.syncState = syncState;
        entry.clashNote = clashNote;
        entry.recordedBy = ctx.caller.userId();
        entry.recordedAt = now;
        boolean created = !ctx.entries.containsKey(key(kind, id));
        entries.saveAndFlush(entry);
        ctx.entries.put(key(kind, id), entry);
        entryHistory.save(new AttendanceHistory(entry.id, created ? "CREATED" : "CORRECTED", outcome, value, reason, ctx.caller.userId(), now));
        changes.record(ctx.caller.userId(), ENTITY, entry.id, "outcome", before, outcome + (value == null ? "" : " " + value.toPlainString()));
        exceptionService.onEntrySaved(ctx.session, entry, ctx.assignedKeys.contains(key(kind, id)));

        if (sync.clash()) {
            return new RowResult(id, kind, AttendanceBridge.SAVED_CLASH, "CLASH", sync.message());
        }
        return new RowResult(id, kind, AttendanceBridge.SAVED, null, null);
    }

    // ------------------------------------------------------------------ context and helpers

    private record Ref(String kind, UUID id) {}

    /** What one save needs: the session, the caller's scope, who is on the roster and who is on leave. */
    private final class Context {
        final Caller caller;
        final TrainingSession session;
        final Scope scope;
        final Map<String, AttendanceEntry> entries = new HashMap<>();
        final Set<String> assignedKeys = new HashSet<>();
        final Set<String> registeredKeys = new HashSet<>();
        Map<UUID, Person> names = Map.of();
        Set<UUID> awayTeachers = Set.of();
        Set<UUID> awayManagers = Set.of();

        Context(Caller caller, TrainingSession session) {
            this.caller = caller;
            this.session = session;
            this.scope = scopes.of(caller);
        }

        String basisOf(String kind, UUID id) {
            AttendanceEntry e = entries.get(key(kind, id));
            if (e != null) {
                return e.basis;
            }
            if (assignedKeys.contains(key(kind, id))) {
                return "ASSIGNED";
            }
            return registeredKeys.contains(key(kind, id)) ? "REGISTERED" : null;
        }

        boolean onLeave(String kind, UUID id) {
            return Assignment.MANAGER.equals(kind) ? awayManagers.contains(id) : awayTeachers.contains(id);
        }
    }

    private Context context(Caller caller, TrainingSession session, List<Ref> refs) {
        Context ctx = new Context(caller, session);
        assignments.findBySessionId(session.id).stream().filter(Assignment::isRequired)
                .forEach(a -> ctx.assignedKeys.add(key(a.personKind, a.personId)));
        registrations.findBySessionId(session.id).stream().filter(Registration::isRegistered)
                .forEach(r -> ctx.registeredKeys.add(key(Assignment.TEACHER, r.teacherId)));
        entries.findBySessionId(session.id).forEach(e -> ctx.entries.put(key(e.personKind, e.personId), e));
        Set<UUID> teacherIds = refs.stream().filter(r -> Assignment.TEACHER.equals(r.kind()) && r.id() != null).map(Ref::id).collect(Collectors.toSet());
        Set<UUID> managerIds = refs.stream().filter(r -> Assignment.MANAGER.equals(r.kind()) && r.id() != null).map(Ref::id).collect(Collectors.toSet());
        ctx.names = people.people(teacherIds, managerIds);
        ctx.awayTeachers = teacherIds.isEmpty() ? Set.of() : attendance.teachersAwayOn(teacherIds, session.sessionDate);
        ctx.awayManagers = managerIds.isEmpty() ? Set.of() : managerAttendance.managersOnLeaveOn(managerIds, session.sessionDate);
        return ctx;
    }

    private TrainingSession lockedOpenSession(UUID sessionId) {
        TrainingSession session = sessions.findForUpdate(sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        if (!TrainingSession.PUBLISHED.equals(session.status) && !TrainingSession.HELD.equals(session.status)) {
            throw new NotFoundException("Session not found.");
        }
        if (session.sessionDate.isAfter(clock.today())) {
            throw new ConflictException("Attendance can only be recorded on or after the day of the session.");
        }
        return session;
    }

    private void markHeld(Caller caller, TrainingSession session) {
        if (TrainingSession.PUBLISHED.equals(session.status)) {
            session.status = TrainingSession.HELD;
            session.heldAt = clock.instant();
            session.updatedAt = clock.instant();
            sessions.saveAndFlush(session);
            sessionHistory.save(new SessionHistory(session.id, "status", TrainingSession.PUBLISHED, TrainingSession.HELD, "First attendance saved", caller.userId(), clock.instant()));
            changes.record(caller.userId(), SessionService.ENTITY, session.id, "status", TrainingSession.PUBLISHED, TrainingSession.HELD);
        }
    }

    private Counts countsFor(Caller caller, TrainingSession session) {
        Scope scope = scopes.of(caller);
        return queries.counts(
                scope,
                assignments.findBySessionId(session.id),
                registrations.findBySessionId(session.id),
                entries.findBySessionId(session.id));
    }

    private boolean allowedOnRoster(Caller caller, Scope scope, String kind, UUID id) {
        if (caller.orgWide()) {
            return true;
        }
        return Assignment.TEACHER.equals(kind) && scope.allowsTeacher(id);
    }

    private Map<UUID, List<HistoryLine>> historyOf(List<AttendanceEntry> recorded) {
        Map<UUID, List<HistoryLine>> out = new HashMap<>();
        if (recorded.isEmpty()) {
            return out;
        }
        for (AttendanceHistory h : entryHistory.findByEntryIdInOrderByChangedAtAsc(recorded.stream().map(e -> e.id).toList())) {
            out.computeIfAbsent(h.entryId, k -> new ArrayList<>()).add(new HistoryLine(h.action, h.outcome, h.dayValue, h.reason, h.changedAt));
        }
        return out;
    }

    private static RowResult refused(RowInput row, String code, String message) {
        return new RowResult(row.personId(), row.personKind(), AttendanceBridge.REFUSED, code, message);
    }

    private static String key(String kind, UUID id) {
        return kind + ":" + id;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimTo(String value, int max) {
        if (blank(value)) {
            return null;
        }
        String t = value.trim();
        if (t.length() > max) {
            throw new InvalidInputException("A note must be at most " + max + " characters.");
        }
        return t;
    }
}
