package com.hls.performance.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.util.Set;
import java.util.UUID;

/** The person doing something: their user id and roles. Admin and Director are organization-wide. */
public record Caller(UUID userId, Set<Role> roles) {

    public boolean orgWide() {
        return CallerContext.isOrgWide(roles);
    }
}
