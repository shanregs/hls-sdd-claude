package com.hls.notification.internal;

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

    public RecipientResolver(
            TeacherDirectory teachers,
            ManagerQueries managers,
            RoleAssignmentRepository roles,
            AppUserRepository users) {
        this.teachers = teachers;
        this.managers = managers;
        this.roles = roles;
        this.users = users;
    }

    /** The user account linked to the Teacher, or empty when the Teacher has none. */
    public Optional<UUID> teacherUser(UUID teacherId) {
        TeacherInfo info = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        return Optional.ofNullable(info).map(TeacherInfo::userId);
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
