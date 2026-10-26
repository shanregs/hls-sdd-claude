package com.hls.training.calendar.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import com.hls.school.api.StaleVersion;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.training.api.TrainingSettings;
import com.hls.training.calendar.internal.PeopleDirectory.Person;
import com.hls.training.calendar.internal.RepeatRule.Fact;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exceptions list (spec 024 US5): who was absent when they were expected, with the repeat marker, the closing of an
 * exception as Excused, Made up or Acknowledged, and a CSV export for Admin and Director. A Manager sees the exceptions of
 * their own Teachers and cannot close them. Callers have checked the grants.
 */
@Service("trainingExceptionBoard")
public class ExceptionBoard {

    private static final int NOTE_MAX = 300;

    public record Filter(String month, UUID zoneId, UUID managerId, String role, String status, int page, int size) {}

    public record CloseBody(String kind, String note, UUID makeupSessionId, Long version) {}

    public record ExceptionRow(
            UUID id,
            String personKind,
            UUID personId,
            String name,
            UUID sessionId,
            String topic,
            LocalDate date,
            String status,
            String closureKind,
            String closureNote,
            UUID makeupSessionId,
            UUID closedBy,
            java.time.Instant closedAt,
            java.time.Instant raisedAt,
            Long version,
            boolean repeat,
            int countInWindow,
            UUID managerId,
            String managerName,
            UUID zoneId,
            String zoneName) {}

    public record Page(List<ExceptionRow> content, int page, int size, long totalElements) {}

    private final ExceptionRepository exceptions;
    private final TrainingSessionRepository sessions;
    private final AttendanceEntryRepository entries;
    private final PeopleDirectory people;
    private final TrainingScope scopes;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final TrainingSettings settings;
    private final ChangeRecorder changes;
    private final TrainingClock clock;

    ExceptionBoard(
            ExceptionRepository exceptions,
            TrainingSessionRepository sessions,
            AttendanceEntryRepository entries,
            PeopleDirectory people,
            TrainingScope scopes,
            TeacherPlacementSource placements,
            SchoolDirectory schools,
            ManagerQueries managers,
            TrainingSettings settings,
            ChangeRecorder changes,
            TrainingClock clock) {
        this.exceptions = exceptions;
        this.sessions = sessions;
        this.entries = entries;
        this.people = people;
        this.scopes = scopes;
        this.placements = placements;
        this.schools = schools;
        this.managers = managers;
        this.settings = settings;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page list(Caller caller, Filter filter) {
        List<ExceptionRow> all = rows(caller, filter);
        int size = Math.max(1, Math.min(filter.size() <= 0 ? 50 : filter.size(), 200));
        int page = Math.max(0, filter.page());
        int from = Math.min(all.size(), page * size);
        return new Page(all.subList(from, Math.min(all.size(), from + size)), page, size, all.size());
    }

    @Transactional(readOnly = true)
    public String exportCsv(Caller caller, Filter filter) {
        StringBuilder out = new StringBuilder("Person,Role,Session,Date,Status,Closure,Note,Repeat,Manager,Zone\n");
        for (ExceptionRow r : rows(caller, new Filter(filter.month(), filter.zoneId(), filter.managerId(), filter.role(), filter.status(), 0, Integer.MAX_VALUE))) {
            out.append(csv(r.name())).append(',').append(r.personKind()).append(',').append(csv(r.topic())).append(',')
                    .append(r.date()).append(',').append(r.status()).append(',').append(csv(r.closureKind())).append(',')
                    .append(csv(r.closureNote())).append(',').append(r.repeat()).append(',').append(csv(r.managerName())).append(',')
                    .append(csv(r.zoneName())).append('\n');
        }
        return out.toString();
    }

    @Transactional
    public ExceptionRow close(Caller caller, UUID id, CloseBody body) {
        TrainingException e = exceptions.findById(id).orElseThrow(() -> new NotFoundException("Exception not found."));
        if (body == null) {
            throw new InvalidInputException("Choose how to close this exception.");
        }
        StaleVersion.check(TrainingException.class, id, e.version, body.version());
        if (!TrainingException.OPEN.equals(e.status)) {
            throw new ConflictException("Only an open exception can be closed.");
        }
        String kind = body.kind();
        if (!"EXCUSED".equals(kind) && !"MADE_UP".equals(kind) && !"ACKNOWLEDGED".equals(kind)) {
            throw new InvalidInputException("Close as Excused, Made up or Acknowledged.");
        }
        String note = SessionRules.optional("The note", body.note(), NOTE_MAX);
        if (("EXCUSED".equals(kind) || "ACKNOWLEDGED".equals(kind)) && note == null) {
            throw new InvalidInputException("A note is required to close as " + ("EXCUSED".equals(kind) ? "Excused." : "Acknowledged."));
        }
        if ("MADE_UP".equals(kind)) {
            requireMakeup(e, body.makeupSessionId());
        }
        e.status = TrainingException.CLOSED;
        e.closureKind = kind;
        e.closureNote = note;
        e.makeupSessionId = "MADE_UP".equals(kind) ? body.makeupSessionId() : null;
        e.closedBy = caller.userId();
        e.closedAt = clock.instant();
        exceptions.saveAndFlush(e);
        changes.record(caller.userId(), ExceptionService.ENTITY, e.id, "status", TrainingException.OPEN, "CLOSED " + kind);
        return rows(caller, new Filter(null, null, null, null, "CLOSED", 0, Integer.MAX_VALUE)).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Exception not found."));
    }

    // ------------------------------------------------------------------ helpers

    private void requireMakeup(TrainingException e, UUID makeupSessionId) {
        if (makeupSessionId == null) {
            throw new InvalidInputException("Choose the later session where the person attended.");
        }
        TrainingSession original = sessions.findById(e.sessionId).orElseThrow(() -> new NotFoundException("Session not found."));
        TrainingSession makeup = sessions.findById(makeupSessionId).orElseThrow(() -> new InvalidInputException("That session was not found."));
        if (!makeup.sessionDate.isAfter(original.sessionDate) || TrainingSession.CANCELLED.equals(makeup.status)) {
            throw new InvalidInputException("The make-up must be a later session that was not cancelled.");
        }
        boolean present = entries.findBySessionIdAndPersonKindAndPersonId(makeupSessionId, e.personKind, e.personId)
                .map(x -> AttendanceEntry.PRESENT.equals(x.outcome))
                .orElse(false);
        if (!present) {
            throw new InvalidInputException("This person is not recorded as present at that session.");
        }
    }

    private List<ExceptionRow> rows(Caller caller, Filter f) {
        Scope scope = scopes.of(caller);
        String wanted = f.status() == null || f.status().isBlank() ? null : f.status().toUpperCase(java.util.Locale.ROOT);
        List<TrainingException> found = exceptions.findAll().stream()
                .filter(e -> wanted != null ? wanted.equals(e.status) : !TrainingException.WITHDRAWN.equals(e.status))
                .filter(e -> f.role() == null || f.role().isBlank() || f.role().equalsIgnoreCase(e.personKind))
                .filter(e -> caller.orgWide() || (Assignment.TEACHER.equals(e.personKind) && scope.allowsTeacher(e.personId)))
                .toList();
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, TrainingSession> sessionById = sessions
                .findByIdIn(found.stream().map(e -> e.sessionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(s -> s.id, s -> s));
        YearMonth month = f.month() == null || f.month().isBlank() ? null : YearMonth.parse(f.month());
        found = found.stream()
                .filter(e -> month == null || YearMonth.from(sessionById.get(e.sessionId).sessionDate).equals(month))
                .toList();

        Set<UUID> teacherIds = found.stream().filter(e -> Assignment.TEACHER.equals(e.personKind)).map(e -> e.personId).collect(Collectors.toSet());
        Set<UUID> managerIds = found.stream().filter(e -> Assignment.MANAGER.equals(e.personKind)).map(e -> e.personId).collect(Collectors.toSet());
        Map<UUID, Person> names = people.people(teacherIds, managerIds);
        LocalDate today = clock.today();
        Map<UUID, TeacherPlacementSource.Assignment> placed = teacherIds.isEmpty() ? Map.of() : placements.currentOf(teacherIds, today);
        Set<UUID> schoolIds = placed.values().stream().map(TeacherPlacementSource.Assignment::schoolId).collect(Collectors.toSet());
        Map<UUID, SchoolInfo> schoolInfo = schoolIds.isEmpty()
                ? Map.of()
                : schools.schools(schoolIds).stream().collect(Collectors.toMap(SchoolInfo::id, s -> s));
        Map<UUID, ManagerRef> managerOfSchool = schoolIds.isEmpty() ? Map.of() : managers.managersOfSchools(schoolIds);
        Map<UUID, ZoneInfo> zoneInfo = schoolInfo.isEmpty()
                ? Map.of()
                : schools.zones(schoolInfo.values().stream().map(SchoolInfo::zoneId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(ZoneInfo::id, z -> z));

        Map<String, List<Fact>> factsByPerson = new HashMap<>();
        for (TrainingException e : exceptions.findAll()) {
            TrainingSession s = sessionById.get(e.sessionId);
            if (s == null) {
                s = sessions.findById(e.sessionId).orElse(null);
            }
            if (s != null) {
                factsByPerson.computeIfAbsent(e.personKind + ":" + e.personId, k -> new ArrayList<>())
                        .add(new Fact(s.sessionDate, e.status, e.closureKind));
            }
        }
        int window = settings.repeatWindowMonths();
        int threshold = settings.repeatThreshold();

        List<ExceptionRow> out = new ArrayList<>();
        for (TrainingException e : found) {
            TrainingSession s = sessionById.get(e.sessionId);
            Person p = names.get(e.personId);
            TeacherPlacementSource.Assignment place = placed.get(e.personId);
            SchoolInfo school = place == null ? null : schoolInfo.get(place.schoolId());
            ManagerRef manager = school == null ? null : managerOfSchool.get(school.id());
            ZoneInfo zone = school == null ? null : zoneInfo.get(school.zoneId());
            if (f.zoneId() != null && (zone == null || !zone.id().equals(f.zoneId()))) {
                continue;
            }
            if (f.managerId() != null && (manager == null || !manager.id().equals(f.managerId()))) {
                continue;
            }
            List<Fact> facts = factsByPerson.getOrDefault(e.personKind + ":" + e.personId, List.of());
            int count = RepeatRule.count(facts, today, window);
            out.add(new ExceptionRow(
                    e.id, e.personKind, e.personId, p == null ? "Unknown" : p.name(), s.id, s.topic, s.sessionDate, e.status,
                    e.closureKind, e.closureNote, e.makeupSessionId, e.closedBy, e.closedAt, e.raisedAt, e.version,
                    count >= threshold, count, manager == null ? null : manager.id(), manager == null ? null : manager.displayName(),
                    zone == null ? null : zone.id(), zone == null ? null : zone.name()));
        }
        out.sort(Comparator.comparing(ExceptionRow::date).reversed().thenComparing(ExceptionRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
