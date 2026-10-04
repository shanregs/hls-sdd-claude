package com.hls.identity.session;

import com.hls.identity.clientcontext.ClientSource;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service session management (FR-015, User Story 5): a signed-in user may list and end only
 * their own sessions, one at a time or all at once, never another user's (contracts/auth-api.md).
 * Listing needs {@code MY_SESSIONS.VIEW} and ending needs {@code MY_SESSIONS.DELETE}; every role holds
 * both by default and the matrix can take either away from a role.
 * Ending the current session, or all of them, is allowed; the web app then signs the user out.
 */
@RestController
public class SessionController {

    private final SessionRepository sessionRepository;
    private final SessionEndingService ending;
    private final PermissionGuard permissionGuard;

    public SessionController(
            SessionRepository sessionRepository, SessionEndingService ending, PermissionGuard permissionGuard) {
        this.sessionRepository = sessionRepository;
        this.ending = ending;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/api/v1/me/sessions")
    public ResponseEntity<List<SessionView>> listSessions(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.MY_SESSIONS, PermissionAction.VIEW);
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        List<SessionView> views = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).stream()
                .sorted(java.util.Comparator.comparing(Session::getSignedInAt))
                .map(session -> new SessionView(
                        session.getId(),
                        session.getDeviceDescription(),
                        session.getSignedInAt(),
                        session.getLastActivityAt(),
                        session.getId().equals(currentSessionId),
                        session.getClientType(),
                        session.getAppVersion()))
                .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping("/api/v1/me/sessions/{sessionId}")
    public ResponseEntity<Void> endSession(
            @PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.MY_SESSIONS, PermissionAction.DELETE);
        UUID userId = UUID.fromString(jwt.getSubject());
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        ending.end(List.of(session), userId, false, httpRequest.getRemoteAddr(), userAgent(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends every active session of the caller, including the one making this request. The web app
     * signs the user out straight after.
     */
    @DeleteMapping("/api/v1/me/sessions")
    public ResponseEntity<Map<String, Integer>> endAllSessions(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.MY_SESSIONS, PermissionAction.DELETE);
        UUID userId = UUID.fromString(jwt.getSubject());
        List<Session> mine = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        int ended = ending.end(mine, userId, false, httpRequest.getRemoteAddr(), userAgent(httpRequest));
        return ResponseEntity.ok(Map.of("ended", ended));
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }

    static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown device";
    }

    /**
     * {@code clientType} (WEB or ANDROID) and {@code appVersion} let the session list show which
     * sessions came from the Android app (spec 018 FR-007).
     */
    public record SessionView(
            UUID id,
            String deviceDescription,
            Instant signedInAt,
            Instant lastActivityAt,
            boolean current,
            ClientSource clientType,
            String appVersion) {}
}
