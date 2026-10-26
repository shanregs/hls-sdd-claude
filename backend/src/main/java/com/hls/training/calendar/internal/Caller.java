package com.hls.training.calendar.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.util.Set;
import java.util.UUID;

/**
 * The person acting: user id, roles from the access token and the grants the controller read from the permission matrix
 * (the services never read the matrix; they only decide what the grants allow).
 */
public record Caller(UUID userId, Set<Role> roles, Grants grants) {

    /** The grants that change what the caller may do, read once per request. */
    public record Grants(
            boolean view,
            boolean create,
            boolean edit,
            boolean delete,
            boolean export,
            boolean attendanceView,
            boolean attendanceEdit,
            boolean registrationView,
            boolean registrationCreate,
            boolean registrationEdit) {

        public static final Grants NONE = new Grants(false, false, false, false, false, false, false, false, false, false);
    }

    public Caller(UUID userId, Set<Role> roles) {
        this(userId, roles, Grants.NONE);
    }

    public boolean orgWide() {
        return CallerContext.isOrgWide(roles);
    }

    public boolean isManager() {
        return roles.contains(Role.MANAGER) && !orgWide();
    }

    public boolean isTeacher() {
        return roles.contains(Role.TEACHER) && !orgWide() && !roles.contains(Role.MANAGER);
    }
}
