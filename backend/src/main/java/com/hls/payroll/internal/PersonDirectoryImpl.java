package com.hls.payroll.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.DesignationInfo;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignment;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.teacher.api.TeacherDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
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

/** The port over the designation (005a), organization (Manager), teacher and identity modules. */
@Service
@Transactional(readOnly = true)
class PersonDirectoryImpl implements PersonDirectory {

    /** The roles whose holders are paid without being a Manager or a Teacher. */
    static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.SYSTEM);

    private final DesignationDirectory designations;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;
    private final AppUserRepository users;
    private final RoleAssignmentRepository roles;

    PersonDirectoryImpl(
            DesignationDirectory designations,
            ManagerQueries managers,
            TeacherDirectory teachers,
            AppUserRepository users,
            RoleAssignmentRepository roles) {
        this.designations = designations;
        this.managers = managers;
        this.teachers = teachers;
        this.users = users;
        this.roles = roles;
    }

    @Override
    public List<DesignationRef> designations(Kind kind, boolean includeRetired) {
        return designations.designations(kind, includeRetired).stream()
                .map(PersonDirectoryImpl::ref)
                .toList();
    }

    @Override
    public Optional<DesignationRef> designation(UUID designationId) {
        return designations.find(designationId).map(PersonDirectoryImpl::ref);
    }

    @Override
    public Map<UUID, DesignationRef> designations(Collection<UUID> designationIds) {
        Map<UUID, DesignationRef> result = new HashMap<>();
        if (!designationIds.isEmpty()) {
            designations.findAll(designationIds).forEach((id, info) -> result.put(id, ref(info)));
        }
        return result;
    }

    @Override
    public Map<UUID, UUID> managerDesignationsOn(Collection<UUID> managerIds, LocalDate date) {
        return managerIds.isEmpty() ? Map.of() : managers.designationsOn(managerIds, date);
    }

    @Override
    public Map<UUID, ManagerDates> managers(Collection<UUID> managerIds) {
        Map<UUID, ManagerDates> result = new HashMap<>();
        if (!managerIds.isEmpty()) {
            managers.employment(managerIds)
                    .forEach((id, e) -> result.put(id, new ManagerDates(e.joiningDate(), e.exitDate())));
        }
        return result;
    }

    @Override
    public List<ManagerSummary> allManagers() {
        return managers.allManagers().stream()
                .map(m -> new ManagerSummary(m.id(), m.displayName(), m.active()))
                .toList();
    }

    @Override
    public Map<UUID, List<UUID>> managerHolders(Collection<UUID> designationIds) {
        return designationIds.isEmpty() ? Map.of() : managers.holdersByDesignation(designationIds);
    }

    @Override
    public Set<UUID> existingTeachers(Collection<UUID> teacherIds) {
        return teacherIds.isEmpty() ? Set.of() : new HashSet<>(teachers.teacherInfo(teacherIds).keySet());
    }

    @Override
    public List<TeacherSummary> allTeachers() {
        return teachers.allTeachers().stream()
                .map(t -> new TeacherSummary(t.id(), t.name(), t.status()))
                .toList();
    }

    @Override
    public Map<UUID, UUID> teacherDesignations(Collection<UUID> teacherIds) {
        return teacherIds.isEmpty() ? Map.of() : teachers.currentDesignations(teacherIds);
    }

    @Override
    public Map<UUID, List<UUID>> teacherHolders(Collection<UUID> designationIds) {
        return teachers.teachersByDesignation(designationIds);
    }

    @Override
    public Map<UUID, RecordedSalary> teacherSalariesOn(Collection<UUID> teacherIds, LocalDate date) {
        Map<UUID, RecordedSalary> result = new HashMap<>();
        teachers.salariesOn(teacherIds, date)
                .forEach((id, s) -> result.put(id, new RecordedSalary(s.amount(), s.effectiveOn())));
        return result;
    }

    @Override
    public Map<UUID, StaffUser> staff(Collection<UUID> userIds) {
        Map<UUID, StaffUser> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        Map<UUID, List<String>> rolesOf = new HashMap<>();
        for (UUID id : new HashSet<>(userIds)) {
            for (RoleAssignment assignment : roles.findByUserId(id)) {
                if (STAFF_ROLES.contains(assignment.getRole())) {
                    rolesOf.computeIfAbsent(id, k -> new ArrayList<>()).add(assignment.getRole().name());
                }
            }
        }
        users.findAllById(rolesOf.keySet())
                .forEach(u -> result.put(u.getId(), staffUser(u, rolesOf.get(u.getId()))));
        return result;
    }

    @Override
    public List<StaffUser> allStaff() {
        Map<UUID, List<String>> rolesOf = new HashMap<>();
        for (Role role : STAFF_ROLES) {
            for (UUID id : roles.userIdsWithRole(role)) {
                rolesOf.computeIfAbsent(id, k -> new ArrayList<>()).add(role.name());
            }
        }
        return users.findAllById(rolesOf.keySet()).stream()
                .map(u -> staffUser(u, rolesOf.get(u.getId())))
                .sorted(Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    @Override
    public List<Placement> placements(Collection<UUID> teacherIds, LocalDate from, LocalDate to) {
        return teachers.placementsOverlapping(teacherIds, from, to).stream()
                .map(p -> new Placement(p.teacherId(), p.schoolId(), p.startsOn(), p.endsOn()))
                .sorted(Comparator.comparing(Placement::from))
                .toList();
    }

    private static StaffUser staffUser(AppUser user, List<String> roles) {
        List<String> sorted = roles.stream().sorted().toList();
        return new StaffUser(user.getId(), user.getDisplayName(), user.isActive(), sorted);
    }

    private static DesignationRef ref(DesignationInfo info) {
        return new DesignationRef(info.id(), info.name(), info.kind(), info.retired());
    }
}
