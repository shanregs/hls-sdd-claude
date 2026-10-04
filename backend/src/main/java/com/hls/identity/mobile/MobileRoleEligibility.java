package com.hls.identity.mobile;

import com.hls.identity.user.Role;
import java.util.Collection;
import java.util.Map;
import org.springframework.http.ResponseEntity;

/**
 * Which accounts may use the Android app (spec 018 FR-003, research.md §10): those holding at least
 * one of Teacher, Manager or Director. Admin and System are web-only. This is enforced by the server
 * after the credentials have been checked, so it can never be used to discover which accounts exist.
 * It is a product rule, not a security control: the client header is self-declared.
 */
public final class MobileRoleEligibility {

    /** Login-history outcome recorded when an otherwise valid sign-in is refused. */
    public static final String REFUSED_OUTCOME = "Role not permitted in the mobile app";

    public static final String WEB_ONLY_CODE = "WEB_ONLY_ROLE";
    public static final String WEB_ONLY_MESSAGE = "Your account uses the HLS web application.";

    private MobileRoleEligibility() {}

    public static boolean isEligible(Collection<Role> roles) {
        return roles.contains(Role.TEACHER) || roles.contains(Role.MANAGER) || roles.contains(Role.DIRECTOR);
    }

    /** The 403 body sent when {@link #isEligible} is false. */
    public static ResponseEntity<Map<String, String>> refusal() {
        return ResponseEntity.status(403).body(Map.of("code", WEB_ONLY_CODE, "message", WEB_ONLY_MESSAGE));
    }
}
