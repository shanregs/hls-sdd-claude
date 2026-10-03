package com.hls.teacher.api;

import com.hls.identity.user.Role;
import java.util.Set;
import java.util.UUID;

/**
 * The shared way for later modules to ask which Teachers a user may access (spec 005 FR-021):
 * Admin/Director all Teachers (including unplaced), a Manager the Teachers placed today at their
 * Schools, a Teacher only their own record. Derived on every call.
 */
public interface TeacherScopeQueries {

    /** {@code teacherIds} is meaningful only when {@code orgWide} is false. */
    record TeacherScope(boolean orgWide, Set<UUID> teacherIds) {

        public boolean allows(UUID teacherId) {
            return orgWide || teacherIds.contains(teacherId);
        }
    }

    TeacherScope teacherScope(UUID userId, Set<Role> roles);

    default boolean isTeacherInScope(UUID userId, Set<Role> roles, UUID teacherId) {
        return teacherScope(userId, roles).allows(teacherId);
    }
}
