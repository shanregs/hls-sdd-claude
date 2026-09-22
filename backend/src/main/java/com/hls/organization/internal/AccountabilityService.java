package com.hls.organization.internal;

import com.hls.organization.api.AccountabilityCommands;
import com.hls.organization.api.AccountabilityQueries;
import com.hls.organization.api.AssignmentConflictException;
import com.hls.organization.api.SchoolManagerNotInZoneException;
import com.hls.organization.api.ZoneNotFoundException;
import com.hls.organization.api.dto.*;
import com.hls.school.api.ZoneQueries;
import com.hls.school.api.dto.SchoolZoneAnswer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements both {@link AccountabilityQueries} and {@link AccountabilityCommands}
 * — deliberately one class: every operation shares the same "current row has no
 * effective_to" model and the same conflict-check discipline (research.md §1/§2),
 * and splitting query/command implementations would just duplicate both.
 *
 * <p>{@code unassigned()}'s "never assigned at all" case (FR-009) can only surface
 * identifiers that appear in these tables at least once (i.e. ended without a
 * replacement) — there is no Teacher/School master table yet to enumerate a truly
 * never-referenced identifier against. This mirrors FR-012's already-documented
 * narrowing (research.md §3), not a new one.
 */
@Service
public class AccountabilityService implements AccountabilityQueries, AccountabilityCommands {

    private final SchoolAssignmentRepository schoolAssignmentRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final ZoneManagerAssignmentRepository zoneManagerAssignmentRepository;
    private final ZoneQueries zoneQueries;
    private final Clock clock;

    public AccountabilityService(SchoolAssignmentRepository schoolAssignmentRepository,
                                  TeacherAssignmentRepository teacherAssignmentRepository,
                                  ZoneManagerAssignmentRepository zoneManagerAssignmentRepository,
                                  ZoneQueries zoneQueries,
                                  Clock clock) {
        this.schoolAssignmentRepository = schoolAssignmentRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.zoneManagerAssignmentRepository = zoneManagerAssignmentRepository;
        this.zoneQueries = zoneQueries;
        this.clock = clock;
    }

    // ---- Queries -----------------------------------------------------------------------

    @Override
    public AccountabilityAnswer currentManagerForSchool(UUID schoolId) {
        return schoolAssignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId)
                .map(a -> AccountabilityAnswer.currentManager(a.getManagerId()))
                .orElseGet(AccountabilityAnswer::unassigned);
    }

    @Override
    public AccountabilityAnswer currentManagerForTeacher(UUID teacherId) {
        return teacherAssignmentRepository.findByTeacherIdAndEffectiveToIsNull(teacherId)
                .map(a -> AccountabilityAnswer.currentManager(a.getManagerId()))
                .orElseGet(AccountabilityAnswer::unassigned);
    }

    @Override
    public AccountabilityAnswer managerForSchoolAsOf(UUID schoolId, Instant asOf) {
        return schoolAssignmentRepository.findBySchoolIdOrderByEffectiveFromAsc(schoolId).stream()
                .filter(a -> isActiveAt(a.getEffectiveFrom(), a.getEffectiveTo(), asOf))
                .findFirst()
                .map(a -> AccountabilityAnswer.currentManager(a.getManagerId()))
                .orElseGet(AccountabilityAnswer::unassigned);
    }

    @Override
    public AccountabilityAnswer managerForTeacherAsOf(UUID teacherId, Instant asOf) {
        return teacherAssignmentRepository.findByTeacherIdOrderByEffectiveFromAsc(teacherId).stream()
                .filter(a -> isActiveAt(a.getEffectiveFrom(), a.getEffectiveTo(), asOf))
                .findFirst()
                .map(a -> AccountabilityAnswer.currentManager(a.getManagerId()))
                .orElseGet(AccountabilityAnswer::unassigned);
    }

    private boolean isActiveAt(Instant from, Instant to, Instant asOf) {
        return !from.isAfter(asOf) && (to == null || to.isAfter(asOf));
    }

    @Override
    public List<AssignmentHistoryEntry> schoolAssignmentHistory(UUID schoolId) {
        return schoolAssignmentRepository.findBySchoolIdOrderByEffectiveFromAsc(schoolId).stream()
                .map(a -> new AssignmentHistoryEntry(
                        a.getId(), a.getManagerId(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getAssignedBy(), a.getAssignedAt()))
                .toList();
    }

    @Override
    public List<AssignmentHistoryEntry> teacherAssignmentHistory(UUID teacherId) {
        return teacherAssignmentRepository.findByTeacherIdOrderByEffectiveFromAsc(teacherId).stream()
                .map(a -> new AssignmentHistoryEntry(
                        a.getId(), a.getManagerId(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getAssignedBy(), a.getAssignedAt()))
                .toList();
    }

    @Override
    public List<PortfolioItem> portfolioForManager(UUID managerId) {
        Stream<PortfolioItem> schools = schoolAssignmentRepository.findByManagerIdAndEffectiveToIsNull(managerId).stream()
                .map(a -> new PortfolioItem(ItemType.SCHOOL, a.getSchoolId(), a.getEffectiveFrom()));
        Stream<PortfolioItem> teachers = teacherAssignmentRepository.findByManagerIdAndEffectiveToIsNull(managerId).stream()
                .map(a -> new PortfolioItem(ItemType.TEACHER, a.getTeacherId(), a.getEffectiveFrom()));
        return Stream.concat(schools, teachers).toList();
    }

    @Override
    public List<UnassignedItem> unassigned(ItemType filter) {
        List<UnassignedItem> result = new ArrayList<>();
        if (filter == null || filter == ItemType.SCHOOL) {
            result.addAll(unassignedSchools());
        }
        if (filter == null || filter == ItemType.TEACHER) {
            result.addAll(unassignedTeachers());
        }
        return result;
    }

    private List<UnassignedItem> unassignedSchools() {
        List<SchoolAssignment> all = schoolAssignmentRepository.findAll();
        Set<UUID> currentIds = all.stream().filter(SchoolAssignment::isCurrent)
                .map(SchoolAssignment::getSchoolId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Instant> lastEndedByschoolId = new HashMap<>();
        for (SchoolAssignment a : all) {
            if (!a.isCurrent() && !currentIds.contains(a.getSchoolId())) {
                lastEndedByschoolId.merge(a.getSchoolId(), a.getEffectiveTo(), (a1, b1) -> a1.isAfter(b1) ? a1 : b1);
            }
        }
        return lastEndedByschoolId.entrySet().stream()
                .map(e -> new UnassignedItem(ItemType.SCHOOL, e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public ZoneCoverage zoneCoverage(UUID zoneId) {
        List<UUID> managerIds = zoneManagerAssignmentRepository.findByZoneIdAndEffectiveToIsNull(zoneId).stream()
                .map(ZoneManagerAssignment::getManagerId)
                .toList();
        List<UUID> schoolIds = zoneQueries.currentSchoolsForZone(zoneId);
        return new ZoneCoverage(zoneId, managerIds, schoolIds);
    }

    private List<UnassignedItem> unassignedTeachers() {
        List<TeacherAssignment> all = teacherAssignmentRepository.findAll();
        Set<UUID> currentIds = all.stream().filter(TeacherAssignment::isCurrent)
                .map(TeacherAssignment::getTeacherId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Instant> lastEndedByTeacherId = new HashMap<>();
        for (TeacherAssignment a : all) {
            if (!a.isCurrent() && !currentIds.contains(a.getTeacherId())) {
                lastEndedByTeacherId.merge(a.getTeacherId(), a.getEffectiveTo(), (a1, b1) -> a1.isAfter(b1) ? a1 : b1);
            }
        }
        return lastEndedByTeacherId.entrySet().stream()
                .map(e -> new UnassignedItem(ItemType.TEACHER, e.getKey(), e.getValue()))
                .toList();
    }

    // ---- Commands ----------------------------------------------------------------------

    @Override
    @Transactional
    public CurrentAssignment assignSchoolManager(UUID schoolId, UUID managerId, UUID endsAssignmentId, UUID actingUserId) {
        // specs/006-zone-scoping FR-003/FR-004: checked first, before any existing
        // no-op/conflict logic, so a Zone-coverage failure is never confused with
        // an assignment conflict (research.md §3).
        SchoolZoneAnswer schoolZone = zoneQueries.currentZoneForSchool(schoolId);
        if (schoolZone.state() != SchoolZoneAnswer.State.CURRENT_ZONE) {
            throw new SchoolManagerNotInZoneException("School " + schoolId + " has no current Zone");
        }
        boolean managerCoversZone = zoneManagerAssignmentRepository
                .findByZoneIdAndManagerIdAndEffectiveToIsNull(schoolZone.zoneId(), managerId)
                .isPresent();
        if (!managerCoversZone) {
            throw new SchoolManagerNotInZoneException(
                    "Manager " + managerId + " does not currently cover School " + schoolId + "'s Zone");
        }

        Optional<SchoolAssignment> current = schoolAssignmentRepository.findBySchoolIdAndEffectiveToIsNull(schoolId);
        Instant now = clock.instant();

        if (endsAssignmentId == null) {
            if (current.isPresent()) {
                if (current.get().getManagerId().equals(managerId)) {
                    // FR-010: no-op, no new row.
                    SchoolAssignment existing = current.get();
                    return new CurrentAssignment(existing.getId(), existing.getManagerId(), existing.getEffectiveFrom());
                }
                // A current assignment exists with a different manager but the caller
                // didn't name it to end — treat as a conflict rather than silently
                // overwrite (FR-011's spirit: never proceed without knowing what's current).
                throw new AssignmentConflictException(
                        "School " + schoolId + " already has an accountable Manager; supply endsAssignmentId to reassign");
            }
            SchoolAssignment created = new SchoolAssignment(UUID.randomUUID(), schoolId, managerId, now, actingUserId, now);
            schoolAssignmentRepository.save(created);
            return new CurrentAssignment(created.getId(), managerId, now);
        }

        int updated = schoolAssignmentRepository.endIfStillCurrent(endsAssignmentId, now);
        if (updated == 0) {
            throw new AssignmentConflictException("Assignment " + endsAssignmentId + " was already changed by someone else");
        }
        SchoolAssignment created = new SchoolAssignment(UUID.randomUUID(), schoolId, managerId, now, actingUserId, now);
        schoolAssignmentRepository.save(created);
        return new CurrentAssignment(created.getId(), managerId, now);
    }

    @Override
    @Transactional
    public CurrentAssignment assignTeacherManager(UUID teacherId, UUID managerId, UUID endsAssignmentId, UUID actingUserId) {
        Optional<TeacherAssignment> current = teacherAssignmentRepository.findByTeacherIdAndEffectiveToIsNull(teacherId);
        Instant now = clock.instant();

        if (endsAssignmentId == null) {
            if (current.isPresent()) {
                if (current.get().getManagerId().equals(managerId)) {
                    TeacherAssignment existing = current.get();
                    return new CurrentAssignment(existing.getId(), existing.getManagerId(), existing.getEffectiveFrom());
                }
                throw new AssignmentConflictException(
                        "Teacher " + teacherId + " already has an accountable Manager; supply endsAssignmentId to reassign");
            }
            TeacherAssignment created = new TeacherAssignment(UUID.randomUUID(), teacherId, managerId, now, actingUserId, now);
            teacherAssignmentRepository.save(created);
            return new CurrentAssignment(created.getId(), managerId, now);
        }

        int updated = teacherAssignmentRepository.endIfStillCurrent(endsAssignmentId, now);
        if (updated == 0) {
            throw new AssignmentConflictException("Assignment " + endsAssignmentId + " was already changed by someone else");
        }
        TeacherAssignment created = new TeacherAssignment(UUID.randomUUID(), teacherId, managerId, now, actingUserId, now);
        teacherAssignmentRepository.save(created);
        return new CurrentAssignment(created.getId(), managerId, now);
    }

    @Override
    @Transactional
    public void endSchoolAssignment(UUID assignmentId, UUID actingUserId) {
        int updated = schoolAssignmentRepository.endIfStillCurrent(assignmentId, clock.instant());
        if (updated == 0) {
            throw new AssignmentConflictException("Assignment " + assignmentId + " was already changed by someone else");
        }
    }

    @Override
    @Transactional
    public void endTeacherAssignment(UUID assignmentId, UUID actingUserId) {
        int updated = teacherAssignmentRepository.endIfStillCurrent(assignmentId, clock.instant());
        if (updated == 0) {
            throw new AssignmentConflictException("Assignment " + assignmentId + " was already changed by someone else");
        }
    }

    @Override
    @Transactional
    public ZoneManagerAssignmentView assignManagerToZone(UUID zoneId, UUID managerId, UUID actingUserId) {
        if (zoneQueries.findById(zoneId).isEmpty()) {
            throw new ZoneNotFoundException("No Zone with id " + zoneId);
        }
        Optional<ZoneManagerAssignment> existing =
                zoneManagerAssignmentRepository.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId);
        if (existing.isPresent()) {
            // No-op on repeat, consistent with every other assignment command in this codebase.
            ZoneManagerAssignment current = existing.get();
            return new ZoneManagerAssignmentView(current.getId(), current.getZoneId(), current.getManagerId(), current.getEffectiveFrom());
        }
        Instant now = clock.instant();
        ZoneManagerAssignment created = new ZoneManagerAssignment(UUID.randomUUID(), zoneId, managerId, now, actingUserId, now);
        zoneManagerAssignmentRepository.save(created);
        return new ZoneManagerAssignmentView(created.getId(), zoneId, managerId, now);
    }

    @Override
    @Transactional
    public void removeManagerFromZone(UUID assignmentId, UUID actingUserId) {
        int updated = zoneManagerAssignmentRepository.endIfStillCurrent(assignmentId, clock.instant());
        if (updated == 0) {
            throw new AssignmentConflictException("Zone-Manager assignment " + assignmentId + " was already changed by someone else");
        }
    }
}
