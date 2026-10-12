package com.hls.substitution.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolContacts;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.StaleVersion;
import com.hls.substitution.api.SchoolInformed;
import com.hls.substitution.api.SubstitutionAssigned;
import com.hls.substitution.api.SubstitutionEnded;
import com.hls.substitution.internal.AvailabilityService.Candidate;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The substitution workflow (spec 017a): list, detail, candidates, assign, release, Manager cover and School informed.
 * Permission is checked by the controller; scope (by School) and every business rule are checked here. Every change is
 * audited, appended to the requirement's history and checked against the version the caller loaded.
 */
@Service
public class SubstitutionService {

    static final int MAX_NOTE = 500;
    static final int MAX_CONTACT = 120;
    public static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate FAR_PAST = LocalDate.of(1900, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);

    public record InformedView(Instant at, UUID byUserId, String byName, String contact, String method, String note) {}

    public record HistoryView(
            String action, String fromStatus, String toStatus, String substituteName, String actorName, String note, Instant at) {}

    public record RequirementView(
            UUID id,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            LocalDate dutyDate,
            BigDecimal dayValue,
            String kind,
            String status,
            UUID substituteTeacherId,
            String substituteName,
            boolean substituteNowUnavailable,
            String coverName,
            InformedView informed,
            String principalContact,
            String note,
            Long version) {}

    public record DetailView(RequirementView requirement, List<HistoryView> history) {}

    public record MyView(UUID id, UUID schoolId, String schoolName, LocalDate dutyDate, BigDecimal dayValue, String status) {}

    private final SubstitutionRepository requirements;
    private final SubstitutionHistoryRepository history;
    private final SubstitutionScope scope;
    private final AvailabilityService availability;
    private final SubstitutionAudit audit;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final SchoolContacts contacts;
    private final ManagerQueries managers;
    private final AttendanceReadApi attendance;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SubstitutionService(
            SubstitutionRepository requirements,
            SubstitutionHistoryRepository history,
            SubstitutionScope scope,
            AvailabilityService availability,
            SubstitutionAudit audit,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            SchoolContacts contacts,
            ManagerQueries managers,
            AttendanceReadApi attendance,
            WorkingCalendar calendar,
            AppUserRepository users,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc,
            Clock clock) {
        this.requirements = requirements;
        this.history = history;
        this.scope = scope;
        this.availability = availability;
        this.audit = audit;
        this.teachers = teachers;
        this.schools = schools;
        this.contacts = contacts;
        this.managers = managers;
        this.attendance = attendance;
        this.calendar = calendar;
        this.users = users;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ---- reads ----

    /** Requirements in the caller's scope; {@code statuses} null means open and assigned. */
    @Transactional(readOnly = true)
    public PageResponse<RequirementView> list(
            UUID userId,
            Set<Role> roles,
            Set<RequirementStatus> statuses,
            UUID schoolId,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        SubstitutionScope.Scope s = scope.of(userId, roles);
        Set<RequirementStatus> wanted = statuses == null || statuses.isEmpty()
                ? EnumSet.of(RequirementStatus.OPEN, RequirementStatus.ASSIGNED)
                : statuses;
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        var result = requirements.search(
                s.all(),
                s.schoolIds(),
                schoolId != null,
                schoolId == null ? new UUID(0L, 0L) : schoolId,
                wanted,
                from == null ? FAR_PAST : from,
                to == null ? FAR_FUTURE : to,
                pageable);
        List<RequirementView> content = views(result.getContent(), false);
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DetailView detail(UUID userId, Set<Role> roles, UUID id) {
        SubstitutionRequirement r = inScope(userId, roles, requirements.findById(id));
        RequirementView view = views(List.of(r), true).get(0);
        return new DetailView(view, historyOf(r));
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(UUID userId, Set<Role> roles, UUID id) {
        SubstitutionRequirement r = inScope(userId, roles, requirements.findById(id));
        if (r.getStatus() == RequirementStatus.CANCELLED) {
            return List.of();
        }
        return availability.candidates(r);
    }

    /** The Teacher's own assignments (never names the absent Teacher). */
    @Transactional(readOnly = true)
    public List<MyView> mine(UUID userId, LocalDate from, LocalDate to) {
        TeacherInfo teacher = teachers.teacherOfUser(userId).orElse(null);
        if (teacher == null) {
            return List.of();
        }
        LocalDate start = from == null ? calendar.businessToday().minusDays(14) : from;
        LocalDate end = to == null ? FAR_FUTURE : to;
        List<SubstitutionRequirement> rows = requirements.credits(List.of(teacher.id()), start, end);
        Map<UUID, String> schoolNames = schoolNames(rows.stream().map(SubstitutionRequirement::getSchoolId).toList());
        return rows.stream()
                .map(r -> new MyView(
                        r.getId(),
                        r.getSchoolId(),
                        schoolNames.getOrDefault(r.getSchoolId(), "-"),
                        r.getDutyDate(),
                        r.getDayValue(),
                        r.getStatus().name()))
                .toList();
    }

    // ---- writes ----

    @Transactional
    public RequirementView assign(UUID actor, Set<Role> roles, UUID id, UUID substituteId, String note, Long version) {
        SubstitutionRequirement r = lockedInScope(actor, roles, id);
        StaleVersion.check(SubstitutionRequirement.class, id, r.getVersion(), version);
        requireLive(r);
        if (substituteId == null) {
            throw new InvalidInputException("Choose the Teacher who will cover.");
        }
        if (substituteId.equals(r.getTeacherId())) {
            throw new ConflictException("The absent Teacher cannot cover their own class.");
        }
        String cleanNote = cleanNote(note);
        if (r.getStatus() == RequirementStatus.ASSIGNED && substituteId.equals(r.getSubstituteTeacherId())) {
            return views(List.of(r), true).get(0);
        }
        // Two requirements for one substitute and date must not both pass the day check at once.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "substitute:" + substituteId + ":" + r.getDutyDate());
        Candidate candidate = availability
                .candidate(r, substituteId)
                .orElseThrow(() -> new NotFoundException("That Teacher is not an active Teacher."));
        if (!candidate.available()) {
            throw new ConflictException(candidate.name() + " is not available: " + candidate.reason() + ".");
        }

        Instant now = clock.instant();
        RequirementStatus before = r.getStatus();
        UUID previous = r.getSubstituteTeacherId();
        String informedBefore = informedText(r);
        r.assign(substituteId, cleanNote, now);
        requirements.saveAndFlush(r);
        HistoryAction action = previous == null ? HistoryAction.ASSIGNED : HistoryAction.REASSIGNED;
        history.save(new SubstitutionHistoryEntry(id, action, before, RequirementStatus.ASSIGNED, substituteId, actor, cleanNote, now));
        audit.status(actor, id, before, RequirementStatus.ASSIGNED, null);
        audit.substitute(actor, id, previous, substituteId);
        audit.informed(actor, id, informedBefore, null);
        String schoolName = schoolName(r.getSchoolId());
        if (previous != null) {
            events.publishEvent(new SubstitutionEnded(
                    id, previous, r.getSchoolId(), schoolName, r.getDutyDate(), "another Teacher was assigned"));
        }
        events.publishEvent(new SubstitutionAssigned(id, substituteId, r.getSchoolId(), schoolName, r.getDutyDate()));
        return views(List.of(r), true).get(0);
    }

    @Transactional
    public RequirementView release(UUID actor, Set<Role> roles, UUID id, String reason, Long version) {
        SubstitutionRequirement r = lockedInScope(actor, roles, id);
        StaleVersion.check(SubstitutionRequirement.class, id, r.getVersion(), version);
        requireLive(r);
        String cleanReason = cleanNote(reason);
        if (cleanReason == null) {
            throw new InvalidInputException("Give a reason for releasing the requirement.");
        }
        if (r.getStatus() == RequirementStatus.OPEN) {
            throw new ConflictException("The requirement is already open.");
        }
        Instant now = clock.instant();
        RequirementStatus before = r.getStatus();
        UUID previous = r.getSubstituteTeacherId();
        String informedBefore = informedText(r);
        r.release(cleanReason, now);
        requirements.saveAndFlush(r);
        history.save(new SubstitutionHistoryEntry(id, HistoryAction.RELEASED, before, RequirementStatus.OPEN, previous, actor, cleanReason, now));
        audit.status(actor, id, before, RequirementStatus.OPEN, cleanReason);
        audit.substitute(actor, id, previous, null);
        audit.informed(actor, id, informedBefore, null);
        if (previous != null) {
            events.publishEvent(new SubstitutionEnded(
                    id, previous, r.getSchoolId(), schoolName(r.getSchoolId()), r.getDutyDate(), cleanReason));
        }
        return views(List.of(r), true).get(0);
    }

    @Transactional
    public RequirementView cover(UUID actor, Set<Role> roles, UUID id, String note, Long version) {
        SubstitutionRequirement r = lockedInScope(actor, roles, id);
        StaleVersion.check(SubstitutionRequirement.class, id, r.getVersion(), version);
        requireLive(r);
        if (r.getStatus() == RequirementStatus.MANAGER_COVER) {
            throw new ConflictException("The Manager is already recorded as covering this class.");
        }
        String cleanNote = cleanNote(note);
        UUID coverUser = roles.contains(Role.MANAGER)
                ? actor
                : managers.managerOfSchool(r.getSchoolId()).map(ManagerRef::userId).orElse(null);
        Instant now = clock.instant();
        RequirementStatus before = r.getStatus();
        UUID previous = r.getSubstituteTeacherId();
        String informedBefore = informedText(r);
        r.cover(coverUser, cleanNote, now);
        requirements.saveAndFlush(r);
        history.save(new SubstitutionHistoryEntry(id, HistoryAction.COVERED, before, RequirementStatus.MANAGER_COVER, null, actor, cleanNote, now));
        audit.status(actor, id, before, RequirementStatus.MANAGER_COVER, cleanNote);
        audit.substitute(actor, id, previous, null);
        audit.informed(actor, id, informedBefore, null);
        if (previous != null) {
            events.publishEvent(new SubstitutionEnded(
                    id, previous, r.getSchoolId(), schoolName(r.getSchoolId()), r.getDutyDate(), "the Manager will cover the class"));
        }
        return views(List.of(r), true).get(0);
    }

    @Transactional
    public RequirementView inform(
            UUID actor, Set<Role> roles, UUID id, String contact, String method, String note, Long version) {
        SubstitutionRequirement r = lockedInScope(actor, roles, id);
        StaleVersion.check(SubstitutionRequirement.class, id, r.getVersion(), version);
        requireLive(r);
        if (r.getStatus() == RequirementStatus.OPEN) {
            throw new ConflictException("There is nothing to tell the School yet: assign a substitute or record that the Manager covers.");
        }
        if (r.isInformed()) {
            throw new ConflictException("The School is already recorded as informed about this outcome.");
        }
        String cleanContact = contact == null ? "" : contact.trim();
        if (cleanContact.isEmpty()) {
            throw new InvalidInputException("Say who at the School was told.");
        }
        if (cleanContact.length() > MAX_CONTACT) {
            throw new InvalidInputException("The contact can be at most " + MAX_CONTACT + " characters.");
        }
        InformMethod how = parseMethod(method);
        String cleanNote = cleanNote(note);
        Instant now = clock.instant();
        r.inform(actor, cleanContact, how, cleanNote, now);
        requirements.saveAndFlush(r);
        history.save(new SubstitutionHistoryEntry(
                id, HistoryAction.INFORMED, r.getStatus(), r.getStatus(), r.getSubstituteTeacherId(), actor, cleanContact + " (" + how + ")", now));
        audit.informed(actor, id, null, informedText(r));
        events.publishEvent(new SchoolInformed(id, r.getSchoolId(), schoolName(r.getSchoolId()), r.getDutyDate(), actor));
        return views(List.of(r), true).get(0);
    }

    // ---- helpers ----

    private SubstitutionRequirement lockedInScope(UUID userId, Set<Role> roles, UUID id) {
        SubstitutionRequirement r = requirements.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Substitution requirement not found."));
        scope.require(userId, roles, r.getSchoolId());
        return r;
    }

    private SubstitutionRequirement inScope(UUID userId, Set<Role> roles, java.util.Optional<SubstitutionRequirement> found) {
        SubstitutionRequirement r = found.orElseThrow(() -> new NotFoundException("Substitution requirement not found."));
        scope.require(userId, roles, r.getSchoolId());
        return r;
    }

    private static void requireLive(SubstitutionRequirement r) {
        if (r.getStatus() == RequirementStatus.CANCELLED) {
            throw new ConflictException("This requirement was cancelled because the absence was withdrawn.");
        }
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE) {
            throw new InvalidInputException("The note can be at most " + MAX_NOTE + " characters.");
        }
        return trimmed;
    }

    private static InformMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new InvalidInputException("Say how the School was told.");
        }
        try {
            return InformMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The method must be one of PHONE, IN_PERSON, EMAIL, MESSAGE, OTHER.");
        }
    }

    private static String informedText(SubstitutionRequirement r) {
        return r.isInformed() ? r.getInformedContact() + " (" + r.getInformedMethod() + ")" : null;
    }

    private String schoolName(UUID schoolId) {
        return schools.school(schoolId).map(SchoolInfo::name).orElse("the School");
    }

    private Map<UUID, String> schoolNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return schools.schools(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name, (a, b) -> a));
    }

    private List<HistoryView> historyOf(SubstitutionRequirement r) {
        List<SubstitutionHistoryEntry> rows = history.findByRequirementIdOrderByAtAscIdAsc(r.getId());
        Map<UUID, String> teacherNames = teacherNames(rows.stream().map(SubstitutionHistoryEntry::getSubstituteTeacherId).toList());
        Map<UUID, String> userNames = userNames(rows.stream().map(SubstitutionHistoryEntry::getActorUserId).toList());
        return rows.stream()
                .map(h -> new HistoryView(
                        h.getAction().name(),
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(),
                        h.getSubstituteTeacherId() == null ? null : teacherNames.get(h.getSubstituteTeacherId()),
                        h.getActorUserId() == null ? "System" : userNames.getOrDefault(h.getActorUserId(), "Unknown user"),
                        h.getNote(),
                        h.getAt()))
                .toList();
    }

    private Map<UUID, String> teacherNames(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return teachers.teacherInfo(distinct).values().stream().collect(Collectors.toMap(TeacherInfo::id, TeacherInfo::name));
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(distinct).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    /** Builds the views with names resolved in bulk; {@code full} adds the substitute check and the Principal contact. */
    private List<RequirementView> views(List<SubstitutionRequirement> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> teacherNames = teacherNames(
                rows.stream().flatMap(r -> Stream.of(r.getTeacherId(), r.getSubstituteTeacherId())).toList());
        Map<UUID, String> schoolNames = schoolNames(rows.stream().map(SubstitutionRequirement::getSchoolId).toList());
        Set<UUID> people = new HashSet<>();
        rows.forEach(r -> {
            if (r.getInformedBy() != null) {
                people.add(r.getInformedBy());
            }
            if (r.getCoverUserId() != null) {
                people.add(r.getCoverUserId());
            }
        });
        Map<UUID, String> userNames = userNames(people);
        return rows.stream()
                .map(r -> {
                    boolean substituteGone = full
                            && r.getSubstituteTeacherId() != null
                            && attendance.teachersAwayOn(List.of(r.getSubstituteTeacherId()), r.getDutyDate())
                                    .contains(r.getSubstituteTeacherId());
                    String principal = full
                            ? java.util.Optional.ofNullable(contacts.contactsOf(r.getSchoolId()).principal())
                                    .map(c -> c.name())
                                    .orElse(null)
                            : null;
                    InformedView informed = r.isInformed()
                            ? new InformedView(
                                    r.getInformedAt(),
                                    r.getInformedBy(),
                                    userNames.getOrDefault(r.getInformedBy(), "Unknown user"),
                                    r.getInformedContact(),
                                    r.getInformedMethod().name(),
                                    r.getInformedNote())
                            : null;
                    return new RequirementView(
                            r.getId(),
                            r.getTeacherId(),
                            teacherNames.getOrDefault(r.getTeacherId(), "Unknown Teacher"),
                            r.getSchoolId(),
                            schoolNames.getOrDefault(r.getSchoolId(), "-"),
                            r.getDutyDate(),
                            r.getDayValue(),
                            r.getKind(),
                            r.getStatus().name(),
                            r.getSubstituteTeacherId(),
                            r.getSubstituteTeacherId() == null ? null : teacherNames.get(r.getSubstituteTeacherId()),
                            substituteGone,
                            r.getCoverUserId() == null ? null : userNames.get(r.getCoverUserId()),
                            informed,
                            principal,
                            r.getNote(),
                            r.getVersion());
                })
                .toList();
    }
}
