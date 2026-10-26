package com.hls.notification.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Works out who a notification goes to (spec 010 research.md): a Teacher's own user, or the supervisors
 * of a School's leave. Reads only; an empty result means there is nobody to tell.
 */
@Component
public class RecipientResolver {

    private final TeacherDirectory teachers;
    private final ManagerQueries managers;
    private final RoleAssignmentRepository roles;
    private final AppUserRepository users;
    private final PermissionMatrixService matrix;

    public RecipientResolver(
            TeacherDirectory teachers,
            ManagerQueries managers,
            RoleAssignmentRepository roles,
            AppUserRepository users,
            PermissionMatrixService matrix) {
        this.teachers = teachers;
        this.managers = managers;
        this.roles = roles;
        this.users = users;
        this.matrix = matrix;
    }

    /** The user account linked to the Teacher, or empty when the Teacher has none. */
    public Optional<UUID> teacherUser(UUID teacherId) {
        TeacherInfo info = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        return Optional.ofNullable(info).map(TeacherInfo::userId);
    }

    /** The Manager with the given id, or empty when there is none (spec 032 notifies the Manager of their own day). */
    public Optional<ManagerQueries.ManagerRef> manager(UUID managerId) {
        return managers.allManagers().stream().filter(m -> m.id().equals(managerId)).findFirst();
    }

    /** Every active user whose role holds Payroll Approve (spec 013b): who hears of a run submitted for approval. */
    public Set<UUID> payrollApprovers() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            if (matrix.isGranted(role, PermissionModule.PAYROLL, PermissionAction.APPROVE)) {
                ids.addAll(roles.userIdsWithRole(role));
            }
        }
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }

    /** Every active user whose role holds Expenses Approve (spec 015): who hears of a claim submitted for review. */
    public Set<UUID> expenseReviewers() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            if (matrix.isGranted(role, PermissionModule.EXPENSES, PermissionAction.APPROVE)) {
                ids.addAll(roles.userIdsWithRole(role));
            }
        }
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }

    /** Every active user whose role holds Content Approve (spec 029): who hears of content submitted for review. */
    public Set<UUID> contentReviewers() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            if (matrix.isGranted(role, PermissionModule.CONTENT, PermissionAction.APPROVE)) {
                ids.addAll(roles.userIdsWithRole(role));
            }
        }
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }

    /** Every active Director: who an escalated task goes to (spec 025). */
    public Set<UUID> activeDirectors() {
        Set<UUID> ids = new LinkedHashSet<>(roles.userIdsWithRole(Role.DIRECTOR));
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }

    /** Every active Admin and Director. */
    public Set<UUID> activeAdminsAndDirectors() {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(roles.userIdsWithRole(Role.ADMIN));
        ids.addAll(roles.userIdsWithRole(Role.DIRECTOR));
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }

    /**
     * Who handles leave for the School: its Manager when that Manager is active, otherwise every active
     * Admin and Director.
     */
    public Set<UUID> leaveSupervisors(UUID schoolId) {
        Optional<ManagerRef> manager = managers.managerOfSchool(schoolId).filter(ManagerRef::active);
        if (manager.isPresent() && manager.get().userId() != null) {
            return Set.of(manager.get().userId());
        }
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(roles.userIdsWithRole(Role.ADMIN));
        ids.addAll(roles.userIdsWithRole(Role.DIRECTOR));
        ids.removeIf(id -> !users.findById(id).filter(AppUser::isActive).isPresent());
        return ids;
    }
}
