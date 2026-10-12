package com.hls.payroll.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.payroll.internal.PersonDirectory.DesignationRef;
import com.hls.payroll.internal.PersonDirectory.ManagerDates;
import com.hls.payroll.internal.PersonDirectory.RecordedSalary;
import com.hls.payroll.internal.PersonDirectory.StaffUser;
import com.hls.payroll.internal.SalaryStructureService.Actor;
import com.hls.payroll.internal.SalaryStructureService.InEffect;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each person's own salary (spec 013a amendment 2026-10-11, User Story 5). A Manager's and a staff member's salary is a
 * dated, append-only history recorded here; a Teacher's is spec 005's record, shown read-only. The designation's amount
 * is offered only as a suggestion. Only holders of SALARY_STRUCTURES reach this service.
 */
@Service
public class PersonSalaryService {

    static final String ENTITY = "PERSON_SALARY";
    static final String EMPLOYMENT_ENTITY = "STAFF_EMPLOYMENT";
    static final String TEACHER_ELSEWHERE = "A Teacher's salary is changed on the Teachers screen.";

    public enum PersonKind {
        TEACHER,
        MANAGER,
        STAFF
    }

    /** One person with the salary in effect today. {@code inEffect} is null when they have none. */
    public record PersonRow(
            PersonKind kind,
            UUID personId,
            String name,
            String designation,
            InEffect inEffect,
            LocalDate joiningDate,
            LocalDate exitDate,
            boolean active,
            boolean salaryEditable,
            List<String> roles) {}

    public record PersonPage(List<PersonRow> content, int page, int size, long totalElements) {}

    public record PersonSalaryRow(
            UUID id, String amount, LocalDate effectiveOn, String note, Actor recordedBy, Instant recordedAt) {}

    public record PersonDetail(PersonRow person, InEffect suggestedDefault, List<PersonSalaryRow> history) {}

    public record Employment(LocalDate joiningDate, LocalDate exitDate) {}

    /** A row together with the designation id it was built from, which the public row does not carry. */
    private record Built(PersonRow row, UUID designationId) {}

    private final PersonDirectory people;
    private final PersonSalaries personSalaries;
    private final PersonSalaryRepository salaries;
    private final StaffEmploymentRepository staffDates;
    private final DesignationSalaryRepository designationDefaults;
    private final AppUserRepository users;
    private final ChangeRecorder changes;
    private final WorkingCalendar calendar;
    private final Clock clock;

    PersonSalaryService(
            PersonDirectory people,
            PersonSalaries personSalaries,
            PersonSalaryRepository salaries,
            StaffEmploymentRepository staffDates,
            DesignationSalaryRepository designationDefaults,
            AppUserRepository users,
            ChangeRecorder changes,
            WorkingCalendar calendar,
            Clock clock) {
        this.people = people;
        this.personSalaries = personSalaries;
        this.salaries = salaries;
        this.staffDates = staffDates;
        this.designationDefaults = designationDefaults;
        this.users = users;
        this.changes = changes;
        this.calendar = calendar;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    /**
     * One page of people with their salary in effect today, by name. {@code withoutSalary} keeps the people who work
     * here today and have none; someone who has exited or is inactive is not a gap.
     */
    @Transactional(readOnly = true)
    public PersonPage list(PersonKind kind, String query, boolean withoutSalary, int page, int size) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<PersonRow> rows = build(kind, calendar.businessToday()).stream()
                .map(Built::row)
                .filter(r -> term.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(term))
                .filter(r -> !withoutSalary || (r.inEffect() == null && r.active()))
                .toList();
        int pageSize = Math.max(1, Math.min(size, 200));
        int from = (int) Math.min((long) Math.max(page, 0) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return new PersonPage(rows.subList(from, to), Math.max(page, 0), pageSize, rows.size());
    }

    @Transactional(readOnly = true)
    public PersonDetail detail(PersonKind kind, UUID personId) {
        LocalDate today = calendar.businessToday();
        Built built = build(kind, today).stream()
                .filter(b -> b.row().personId().equals(personId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Person not found."));
        List<PersonSalary> history = kind == PersonKind.TEACHER
                ? List.of()
                : salaries.findByPersonKindAndPersonIdOrderByEffectiveOnDescRecordedAtDesc(
                        PersonSalary.Kind.valueOf(kind.name()), personId);
        Map<UUID, String> names = names(history);
        return new PersonDetail(
                built.row(),
                suggestedDefault(built.designationId(), today),
                SalaryResolver.newestFirst(history).stream().map(r -> row(r, names)).toList());
    }

    // ----------------------------------------------------------------- commands

    /**
     * Records a person's own salary from an effective date. Refused with the problem named when the amount is missing,
     * zero, negative, has more than two decimals or exceeds the maximum, when the date is missing or before the first
     * day of the current month, or when the note is over 500 characters. A Teacher's salary is not written here.
     */
    @Transactional
    public PersonSalaryRow record(
            UUID actor, PersonKind kind, UUID personId, BigDecimal amount, LocalDate effectiveOn, String note) {
        if (kind == PersonKind.TEACHER) {
            throw new InvalidInputException(TEACHER_ELSEWHERE);
        }
        BigDecimal cleanAmount = SalaryStructureService.amount(amount);
        LocalDate date = PayrollInput.effectiveDate(effectiveOn, calendar.businessToday());
        String cleanNote = PayrollInput.note(note);
        requireExists(kind, personId);

        PersonSalary.Kind storedKind = PersonSalary.Kind.valueOf(kind.name());
        BigDecimal prior = salaries
                .findFirstByPersonKindAndPersonIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(
                        storedKind, personId, date)
                .map(PersonSalary::getAmount)
                .orElse(null);
        PersonSalary saved = salaries.saveAndFlush(
                new PersonSalary(storedKind, personId, cleanAmount, date, cleanNote, actor, clock.instant()));
        if (prior != null && prior.compareTo(cleanAmount) == 0) {
            changes.recordLifecycle(
                    actor, ENTITY, personId, "amount", "re-recorded " + SalaryStructureService.money(cleanAmount) + " from " + date);
        } else {
            changes.record(
                    actor,
                    ENTITY,
                    personId,
                    "amount",
                    prior == null ? null : SalaryStructureService.money(prior),
                    SalaryStructureService.money(cleanAmount));
        }
        return row(saved, names(List.of(saved)));
    }

    /** Sets a staff member's joining and exit dates; each changed date is audited. */
    @Transactional
    public Employment setEmployment(UUID actor, UUID userId, LocalDate joining, LocalDate exit) {
        requireExists(PersonKind.STAFF, userId);
        if (joining != null && exit != null && exit.isBefore(joining)) {
            throw new InvalidInputException("The exit date cannot be before the joining date.");
        }
        StaffEmployment existing = staffDates.findById(userId).orElse(null);
        LocalDate before = existing == null ? null : existing.getJoiningDate();
        LocalDate beforeExit = existing == null ? null : existing.getExitDate();
        if (existing == null) {
            staffDates.save(new StaffEmployment(userId, joining, exit, actor, clock.instant()));
        } else {
            existing.change(joining, exit, actor, clock.instant());
            staffDates.save(existing);
        }
        staffDates.flush();
        changes.record(actor, EMPLOYMENT_ENTITY, userId, "joiningDate", before, joining);
        changes.record(actor, EMPLOYMENT_ENTITY, userId, "exitDate", beforeExit, exit);
        return new Employment(joining, exit);
    }

    // ------------------------------------------------------------------ helpers

    private void requireExists(PersonKind kind, UUID personId) {
        boolean exists = switch (kind) {
            case MANAGER -> people.managers(List.of(personId)).containsKey(personId);
            case STAFF -> people.staff(List.of(personId)).containsKey(personId);
            case TEACHER -> people.existingTeachers(List.of(personId)).contains(personId);
        };
        if (!exists) {
            throw new NotFoundException("Person not found.");
        }
    }

    /** Every person of the kind (all kinds when null) with their salary in effect today, by name. */
    private List<Built> build(PersonKind only, LocalDate today) {
        List<Built> all = new ArrayList<>();
        if (only == null || only == PersonKind.MANAGER) {
            all.addAll(managers(today));
        }
        if (only == null || only == PersonKind.TEACHER) {
            all.addAll(teachers(today));
        }
        if (only == null || only == PersonKind.STAFF) {
            all.addAll(staff(today));
        }
        all.sort(Comparator.comparing((Built b) -> b.row().name().toLowerCase(Locale.ROOT))
                .thenComparing(b -> b.row().kind()));
        return all;
    }

    private List<Built> managers(LocalDate today) {
        List<PersonDirectory.ManagerSummary> managers = people.allManagers();
        Set<UUID> ids = managers.stream().map(PersonDirectory.ManagerSummary::id).collect(Collectors.toSet());
        Map<UUID, ManagerDates> dates = people.managers(ids);
        Map<UUID, UUID> designationOf = people.managerDesignationsOn(ids, today);
        Map<UUID, DesignationRef> designations = people.designations(designationOf.values());
        Map<UUID, PersonSalary> salary = personSalaries.inEffect(PersonSalary.Kind.MANAGER, ids, today);
        List<Built> rows = new ArrayList<>();
        for (PersonDirectory.ManagerSummary m : managers) {
            ManagerDates d = dates.get(m.id());
            LocalDate joining = d == null ? null : d.joiningDate();
            LocalDate exit = d == null ? null : d.exitDate();
            UUID designationId = designationOf.get(m.id());
            DesignationRef designation = designationId == null ? null : designations.get(designationId);
            rows.add(new Built(
                    new PersonRow(
                            PersonKind.MANAGER,
                            m.id(),
                            m.name(),
                            designation == null ? null : designation.name(),
                            inEffect(salary.get(m.id())),
                            joining,
                            exit,
                            m.active() && employedOn(joining, exit, today),
                            true,
                            List.of()),
                    designationId));
        }
        return rows;
    }

    private List<Built> teachers(LocalDate today) {
        List<PersonDirectory.TeacherSummary> teachers = people.allTeachers();
        Set<UUID> ids = teachers.stream().map(PersonDirectory.TeacherSummary::id).collect(Collectors.toSet());
        Map<UUID, UUID> designationOf = people.teacherDesignations(ids);
        Map<UUID, DesignationRef> designations = people.designations(designationOf.values());
        Map<UUID, RecordedSalary> salary = people.teacherSalariesOn(ids, today);
        List<Built> rows = new ArrayList<>();
        for (PersonDirectory.TeacherSummary t : teachers) {
            UUID designationId = designationOf.get(t.id());
            DesignationRef designation = designationId == null ? null : designations.get(designationId);
            RecordedSalary own = salary.get(t.id());
            rows.add(new Built(
                    new PersonRow(
                            PersonKind.TEACHER,
                            t.id(),
                            t.name(),
                            designation == null ? null : designation.name(),
                            own == null ? null : new InEffect(SalaryStructureService.money(own.amount()), own.effectiveOn()),
                            null,
                            null,
                            "ACTIVE".equals(t.status()),
                            false,
                            List.of()),
                    designationId));
        }
        return rows;
    }

    private List<Built> staff(LocalDate today) {
        List<StaffUser> staff = people.allStaff();
        Set<UUID> ids = staff.stream().map(StaffUser::id).collect(Collectors.toSet());
        Map<UUID, StaffEmployment> dates = ids.isEmpty()
                ? Map.of()
                : staffDates.findAllById(ids).stream().collect(Collectors.toMap(StaffEmployment::getUserId, d -> d));
        Map<UUID, PersonSalary> salary = personSalaries.inEffect(PersonSalary.Kind.STAFF, ids, today);
        List<Built> rows = new ArrayList<>();
        for (StaffUser u : staff) {
            StaffEmployment d = dates.get(u.id());
            LocalDate joining = d == null ? null : d.getJoiningDate();
            LocalDate exit = d == null ? null : d.getExitDate();
            rows.add(new Built(
                    new PersonRow(
                            PersonKind.STAFF,
                            u.id(),
                            u.name(),
                            null,
                            inEffect(salary.get(u.id())),
                            joining,
                            exit,
                            u.active() && employedOn(joining, exit, today),
                            true,
                            u.roles()),
                    null));
        }
        return rows;
    }

    private InEffect suggestedDefault(UUID designationId, LocalDate today) {
        if (designationId == null) {
            return null;
        }
        List<DesignationSalary> rows = designationDefaults.findCandidates(List.of(designationId), today);
        return SalaryResolver.inEffect(rows, today)
                .map(r -> new InEffect(SalaryStructureService.money(r.getAmount()), r.effectiveOn()))
                .orElse(null);
    }

    private static InEffect inEffect(PersonSalary row) {
        return row == null ? null : new InEffect(SalaryStructureService.money(row.getAmount()), row.effectiveOn());
    }

    private static boolean employedOn(LocalDate joining, LocalDate exit, LocalDate date) {
        return (joining == null || !date.isBefore(joining)) && (exit == null || !date.isAfter(exit));
    }

    private PersonSalaryRow row(PersonSalary r, Map<UUID, String> names) {
        return new PersonSalaryRow(
                r.getId(),
                SalaryStructureService.money(r.getAmount()),
                r.effectiveOn(),
                r.getNote(),
                new Actor(r.getRecordedBy(), names.getOrDefault(r.getRecordedBy(), "Unknown user")),
                r.recordedAt());
    }

    private Map<UUID, String> names(Collection<PersonSalary> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> ids.add(Objects.requireNonNull(r.getRecordedBy())));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (AppUser user : users.findAllById(ids)) {
            names.put(user.getId(), user.getDisplayName());
        }
        return names;
    }
}
