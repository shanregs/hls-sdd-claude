package com.hls.training.calendar.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.util.Set;
import java.util.UUID;

/** The person acting: user id and roles from the access token (the controller has already checked the grants). */
public record Caller(UUID userId, Set<Role> roles) {

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
