package com.hls.transfer.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.util.Set;
import java.util.UUID;

/**
 * The person acting: user id, roles and the two grants that change what they may do with a request (the controller
 * reads them from the permission matrix; a Teacher or System never reaches the service).
 */
public record Caller(UUID userId, Set<Role> roles, boolean canEdit, boolean canApprove) {

    public boolean orgWide() {
        return CallerContext.isOrgWide(roles);
    }
}
