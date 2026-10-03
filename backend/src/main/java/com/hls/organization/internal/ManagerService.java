package com.hls.organization.internal;

import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import com.hls.identity.user.UserAdminService.UserWithRoles;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerView;
import com.hls.organization.api.ManagerViewEnricher;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager records and the Zone/School assignments (spec 005 US3). Every operation that changes or
 * checks a Manager's assignments first takes a pessimistic lock on that Manager's row, so the
 * invariant "a School's Manager covers its Zone" cannot be broken by concurrent requests (FR-009).
 */
@Service
public class ManagerService implements ManagerQueries {

    private final ManagerRepository managerRepository;
    private final ZoneManagerAssignmentRepository zoneAssignments;
    private final SchoolManagerAssignmentRepository schoolAssignments;
    private final SchoolDirectory schoolDirectory;
    private final UserAdminService userAdminService;
    private final List<ManagerViewEnricher> enrichers;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ManagerService(
            ManagerRepository managerRepository,
            ZoneManagerAssignmentRepository zoneAssignments,
            SchoolManagerAssignmentRepository schoolAssignments,
            SchoolDirectory schoolDirectory,
            UserAdminService userAdminService,
            List<ManagerViewEnricher> enrichers,
            ChangeRecorder changes,
            Clock clock) {
        this.managerRepository = managerRepository;
        this.zoneAssignments = zoneAssignments;
        this.schoolAssignments = schoolAssignments;
        this.schoolDirectory = schoolDirectory;
        this.userAdminService = userAdminService;
        this.enrichers = enrichers;
        this.changes = changes;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    /** An active user holding the Manager role who has no Manager record yet. */
    public record Candidate(UUID userId, String displayName, String phone) {}

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        Set<UUID> taken = managerRepository.findAll().stream().map(Manager::getUserId).collect(Collectors.toSet());
        return userAdminService
                .search("", Role.MANAGER, true, org.springframework.data.domain.PageRequest.of(0, 500))
                .getContent()
                .stream()
                .filter(u -> !taken.contains(u.user().getId()))
                .map(u -> new Candidate(u.user().getId(), u.user().getDisplayName(), u.user().getPhone()))
                .sorted(Comparator.comparing(c -> c.displayName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ManagerView> list(String query, Pageable pageable) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Manager> all = managerRepository.findAll();
        Map<UUID, UserWithRoles> users = usersOf(all);
        List<ManagerView> views = viewsOf(all, users, false).stream()
                .filter(v -> term.isEmpty()
                        || v.displayName().toLowerCase(Locale.ROOT).contains(term)
                        || (v.phone() != null && v.phone().contains(term)))
                .sorted(Comparator.comparing(v -> v.displayName().toLowerCase(Locale.ROOT)))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), views.size());
        int end = Math.min(start + pageable.getPageSize(), views.size());
        return new PageImpl<>(views.subList(start, end), pageable, views.size());
    }

    @Transactional(readOnly = true)
    public ManagerView get(UUID id) {
        Manager manager = managerRepository.findById(id).orElseThrow(() -> new NotFoundException("Manager not found."));
        return viewsOf(List.of(manager), usersOf(List.of(manager)), true).get(0);
    }

    @Transactional(readOnly = true)
    public List<ManagerView.AssignmentRow> schoolHistory(UUID schoolId) {
        schoolDirectory.school(schoolId).orElseThrow(() -> new NotFoundException("School not found."));
        List<SchoolManagerAssignment> rows = schoolAssignments.findBySchoolIdOrderByStartsOnDesc(schoolId);
        Map<UUID, Manager> managers = managersById(rows.stream().map(SchoolManagerAssignment::getManagerId).toList());
        Map<UUID, UserWithRoles> users = usersOf(managers.values());
        return rows.stream()
                .map(r -> new ManagerView.AssignmentRow(
                        "SCHOOL_MANAGER",
                        r.getManagerId(),
                        nameOf(users, managers.get(r.getManagerId())),
                        r.getStartsOn(),
                        r.getEndsOn()))
                .toList();
    }

    // -------------------------------------------------------------- ManagerQueries

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagerRef> managerOfSchool(UUID schoolId) {
        return schoolAssignments
                .findBySchoolIdAndEndsOnIsNull(schoolId)
                .flatMap(r -> managerRepository.findById(r.getManagerId()))
                .map(m -> refOf(m, usersOf(List.of(m))));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ManagerRef> managersOfSchools(Collection<UUID> schoolIds) {
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        List<SchoolManagerAssignment> rows = schoolAssignments.findBySchoolIdInAndEndsOnIsNull(schoolIds);
        Map<UUID, Manager> managers = managersById(rows.stream().map(SchoolManagerAssignment::getManagerId).toList());
        Map<UUID, UserWithRoles> users = usersOf(managers.values());
        Map<UUID, ManagerRef> result = new HashMap<>();
        for (SchoolManagerAssignment row : rows) {
            Manager manager = managers.get(row.getManagerId());
            if (manager != null) {
                result.put(row.getSchoolId(), refOf(manager, users));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagerRef> managerByUserId(UUID userId) {
        return managerRepository.findByUserId(userId).map(m -> refOf(m, usersOf(List.of(m))));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Set<UUID>> currentSchoolIds(Collection<UUID> managerIds) {
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        return schoolAssignments.findByManagerIdInAndEndsOnIsNull(managerIds).stream()
                .collect(Collectors.groupingBy(
                        SchoolManagerAssignment::getManagerId,
                        Collectors.mapping(SchoolManagerAssignment::getSchoolId, Collectors.toSet())));
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public ManagerView create(UUID actor, UUID userId) {
        if (userId == null) {
            throw new InvalidInputException("A user is required.");
        }
        UserWithRoles user =
                userAdminService.find(userId).orElseThrow(() -> new InvalidInputException("User not found."));
        if (!user.roles().contains(Role.MANAGER)) {
            throw new InvalidInputException("Only a user who holds the Manager role can have a Manager record.");
        }
        if (managerRepository.findByUserId(userId).isPresent()) {
            throw new ConflictException("This user already has a Manager record.");
        }
        Manager manager = new Manager(userId, clock.instant());
        manager.setActive(user.user().isActive(), clock.instant());
        manager = managerRepository.saveAndFlush(manager);
        changes.recordLifecycle(actor, "MANAGER", manager.getId(), "created", user.user().getDisplayName());
        return viewsOf(List.of(manager), Map.of(userId, user), false).get(0);
    }

    /** Replaces the Manager's current Zone set (FR-008); refuses to drop a Zone where they still have Schools. */
    @Transactional
    public ManagerView setZones(UUID actor, UUID managerId, Collection<UUID> zoneIds, Long version) {
        Manager manager = lock(List.of(managerId)).get(0);
        StaleVersion.check(Manager.class, managerId, manager.getVersion(), version);
        Set<UUID> wanted = new LinkedHashSet<>(zoneIds == null ? List.of() : zoneIds);
        Set<UUID> known = schoolDirectory.zones(wanted).stream()
                .map(SchoolDirectory.ZoneInfo::id)
                .collect(Collectors.toSet());
        if (!known.containsAll(wanted)) {
            throw new InvalidInputException("One of the Zones does not exist.");
        }

        List<ZoneManagerAssignment> current = zoneAssignments.findByManagerIdAndEndsOnIsNull(managerId);
        Set<UUID> currentZoneIds = current.stream().map(ZoneManagerAssignment::getZoneId).collect(Collectors.toSet());
        Set<UUID> removed = new HashSet<>(currentZoneIds);
        removed.removeAll(wanted);

        if (!removed.isEmpty()) {
            List<String> stranded = new ArrayList<>();
            List<SchoolManagerAssignment> schools = schoolAssignments.findByManagerIdAndEndsOnIsNull(managerId);
            Map<UUID, SchoolInfo> infos = schoolDirectory
                    .schools(schools.stream().map(SchoolManagerAssignment::getSchoolId).toList())
                    .stream()
                    .collect(Collectors.toMap(SchoolInfo::id, s -> s));
            for (SchoolManagerAssignment row : schools) {
                SchoolInfo info = infos.get(row.getSchoolId());
                if (info != null && removed.contains(info.zoneId())) {
                    stranded.add(info.name());
                }
            }
            if (!stranded.isEmpty()) {
                throw new ConflictException("This Manager still has Schools in that Zone: "
                        + String.join(", ", stranded) + ". Reassign them first.");
            }
        }

        LocalDate today = LocalDate.now(clock);
        for (ZoneManagerAssignment row : current) {
            if (removed.contains(row.getZoneId())) {
                row.end(today);
                zoneAssignments.save(row);
                changes.record(actor, "ZONE_MANAGER_ASSIGNMENT", managerId + "/" + row.getZoneId(), "assigned", true, false);
            }
        }
        for (UUID zoneId : wanted) {
            if (!currentZoneIds.contains(zoneId)) {
                zoneAssignments.save(new ZoneManagerAssignment(zoneId, managerId, today));
                changes.record(actor, "ZONE_MANAGER_ASSIGNMENT", managerId + "/" + zoneId, "assigned", false, true);
            }
        }
        manager.touch(clock.instant());
        managerRepository.saveAndFlush(manager);
        return viewsOf(List.of(manager), usersOf(List.of(manager)), true).get(0);
    }

    /**
     * Assigns {@code managerId} (or none, when null) as the School's Manager (FR-009). The Manager
     * must cover the School's Zone; the old assignment ends and the new one starts in one transaction.
     */
    @Transactional
    public void assignSchoolManager(UUID actor, UUID schoolId, UUID managerId) {
        SchoolInfo school = schoolDirectory.school(schoolId).orElseThrow(() -> new NotFoundException("School not found."));
        Optional<SchoolManagerAssignment> currentRow = schoolAssignments.findBySchoolIdAndEndsOnIsNull(schoolId);
        Set<UUID> toLock = new HashSet<>();
        currentRow.ifPresent(r -> toLock.add(r.getManagerId()));
        if (managerId != null) {
            toLock.add(managerId);
        }
        if (!toLock.isEmpty()) {
            lock(toLock);
        }
        // re-read after taking the lock: a concurrent request may have changed the row
        currentRow = schoolAssignments.findBySchoolIdAndEndsOnIsNull(schoolId);

        if (managerId != null) {
            Manager manager =
                    managerRepository.findById(managerId).orElseThrow(() -> new NotFoundException("Manager not found."));
            if (!manager.isActive()) {
                throw new ConflictException("This Manager is not active.");
            }
            if (!zoneAssignments.existsByZoneIdAndManagerIdAndEndsOnIsNull(school.zoneId(), managerId)) {
                String zoneName = schoolDirectory.zone(school.zoneId()).map(SchoolDirectory.ZoneInfo::name).orElse("?");
                throw new ConflictException(
                        "This Manager does not cover the School's Zone (" + zoneName + "). Assign them to that Zone first.");
            }
            if (currentRow.isPresent() && currentRow.get().getManagerId().equals(managerId)) {
                return;
            }
        } else if (currentRow.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        UUID before = currentRow.map(SchoolManagerAssignment::getManagerId).orElse(null);
        currentRow.ifPresent(row -> {
            row.end(today);
            schoolAssignments.saveAndFlush(row);
        });
        if (managerId != null) {
            schoolAssignments.save(new SchoolManagerAssignment(schoolId, managerId, today));
        }
        changes.record(actor, "SCHOOL_MANAGER_ASSIGNMENT", schoolId, "manager", before, managerId);
    }

    /** Called by the guard SPIs: locks the Manager rows for the rest of the caller's transaction. */
    List<Manager> lock(Collection<UUID> managerIds) {
        List<Manager> locked = managerRepository.lockAll(managerIds);
        if (locked.size() != managerIds.stream().distinct().count()) {
            throw new NotFoundException("Manager not found.");
        }
        return locked;
    }

    // ------------------------------------------------------------------ helpers

    private List<ManagerView> viewsOf(List<Manager> managers, Map<UUID, UserWithRoles> users, boolean withHistory) {
        if (managers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = managers.stream().map(Manager::getId).toList();
        Map<UUID, List<ZoneManagerAssignment>> zonesByManager = zoneAssignments.findByManagerIdInAndEndsOnIsNull(ids).stream()
                .collect(Collectors.groupingBy(ZoneManagerAssignment::getManagerId));
        Map<UUID, Long> schoolCounts = schoolAssignments.findByManagerIdInAndEndsOnIsNull(ids).stream()
                .collect(Collectors.groupingBy(SchoolManagerAssignment::getManagerId, Collectors.counting()));
        Set<UUID> allZoneIds = zonesByManager.values().stream()
                .flatMap(List::stream)
                .map(ZoneManagerAssignment::getZoneId)
                .collect(Collectors.toSet());
        Map<UUID, String> zoneNames = schoolDirectory.zones(allZoneIds).stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<UUID, Map<String, Object>> extras = new HashMap<>();
        for (ManagerViewEnricher enricher : enrichers) {
            enricher.enrich(ids).forEach((id, attrs) -> extras.computeIfAbsent(id, k -> new HashMap<>()).putAll(attrs));
        }
        return managers.stream()
                .map(m -> {
                    UserWithRoles user = users.get(m.getUserId());
                    List<ManagerView.ZoneRef> zones = zonesByManager.getOrDefault(m.getId(), List.of()).stream()
                            .map(r -> new ManagerView.ZoneRef(r.getZoneId(), zoneNames.getOrDefault(r.getZoneId(), "?")))
                            .sorted(Comparator.comparing(ManagerView.ZoneRef::name))
                            .toList();
                    return new ManagerView(
                            m.getId(),
                            m.getUserId(),
                            nameOf(users, m),
                            user == null ? null : user.user().getPhone(),
                            m.isActive(),
                            m.getVersion(),
                            zones,
                            schoolCounts.getOrDefault(m.getId(), 0L),
                            withHistory ? historyOf(m, zoneNames) : null,
                            extras.getOrDefault(m.getId(), Map.of()));
                })
                .toList();
    }

    private List<ManagerView.AssignmentRow> historyOf(Manager manager, Map<UUID, String> currentZoneNames) {
        List<ManagerView.AssignmentRow> rows = new ArrayList<>();
        List<ZoneManagerAssignment> zones = zoneAssignments.findByManagerIdOrderByStartsOnDesc(manager.getId());
        Map<UUID, String> names = new HashMap<>(currentZoneNames);
        schoolDirectory.zones(zones.stream().map(ZoneManagerAssignment::getZoneId).distinct().toList())
                .forEach(z -> names.put(z.id(), z.name()));
        for (ZoneManagerAssignment z : zones) {
            rows.add(new ManagerView.AssignmentRow(
                    "ZONE_MANAGER", z.getZoneId(), names.getOrDefault(z.getZoneId(), "?"), z.getStartsOn(), z.getEndsOn()));
        }
        List<SchoolManagerAssignment> schools = schoolAssignments.findByManagerIdOrderByStartsOnDesc(manager.getId());
        Map<UUID, String> schoolNames = schoolDirectory
                .schools(schools.stream().map(SchoolManagerAssignment::getSchoolId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
        for (SchoolManagerAssignment s : schools) {
            rows.add(new ManagerView.AssignmentRow(
                    "SCHOOL_MANAGER",
                    s.getSchoolId(),
                    schoolNames.getOrDefault(s.getSchoolId(), "?"),
                    s.getStartsOn(),
                    s.getEndsOn()));
        }
        return rows;
    }

    private Map<UUID, UserWithRoles> usersOf(Collection<Manager> managers) {
        Map<UUID, UserWithRoles> users = new HashMap<>();
        for (Manager manager : managers) {
            userAdminService.find(manager.getUserId()).ifPresent(u -> users.put(manager.getUserId(), u));
        }
        return users;
    }

    private Map<UUID, Manager> managersById(Collection<UUID> ids) {
        return managerRepository.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Manager::getId, m -> m));
    }

    private static String nameOf(Map<UUID, UserWithRoles> users, Manager manager) {
        if (manager == null) {
            return "?";
        }
        UserWithRoles user = users.get(manager.getUserId());
        return user == null ? "Unknown user" : user.user().getDisplayName();
    }

    private static ManagerRef refOf(Manager manager, Map<UUID, UserWithRoles> users) {
        return new ManagerRef(manager.getId(), manager.getUserId(), nameOf(users, manager), manager.isActive());
    }
}
