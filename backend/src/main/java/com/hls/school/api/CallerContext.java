package com.hls.school.api;

import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller's id and roles from the access token; shared by the master-data controllers. */
public final class CallerContext {

    private CallerContext() {}

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static Set<Role> roles(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }

    /** Admin and Director are organization-wide (Constitution Principle III). */
    public static boolean isOrgWide(Set<Role> roles) {
        return roles.contains(Role.ADMIN) || roles.contains(Role.DIRECTOR);
    }
}
