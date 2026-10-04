package com.hls.identity.session;

import com.hls.identity.clientcontext.ClientSource;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every user's active sessions, for System (spec 001 FR-015a): list them (all, or one user's), end one,
 * or end all of one user's or of everyone's. Needs {@code SESSION_MANAGEMENT} (View to list, Delete to
 * end), which only System holds by default. An ended session's token stops working at once, so that
 * device is signed out on its next request.
 */
@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

    static final int MAX_PAGE_SIZE = 100;

    private final SessionRepository sessions;
    private final AppUserRepository users;
    private final SessionEndingService ending;
    private final PermissionGuard permissionGuard;

    public AdminSessionController(
            SessionRepository sessions,
            AppUserRepository users,
            SessionEndingService ending,
            PermissionGuard permissionGuard) {
        this.sessions = sessions;
        this.users = users;
        this.ending = ending;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<SessionPage> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.SESSION_MANAGEMENT, PermissionAction.VIEW);
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "signedInAt"));
        Page<Session> result = userId == null
                ? sessions.findByStatus(SessionStatus.ACTIVE, request)
                : sessions.findByUserIdAndStatus(userId, SessionStatus.ACTIVE, request);
        Map<UUID, AppUser> owners = users.findAllById(
                        result.getContent().stream().map(Session::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<AdminSessionView> content = result.getContent().stream()
                .map(s -> {
                    AppUser owner = owners.get(s.getUserId());
                    return new AdminSessionView(
                            s.getId(),
                            s.getUserId(),
                            owner == null ? null : owner.getDisplayName(),
                            owner == null ? null : owner.getPhone(),
                            s.getDeviceDescription(),
                            s.getSignedInAt(),
                            s.getLastActivityAt(),
                            s.getId().equals(currentSessionId),
                            s.getClientType(),
                            s.getAppVersion());
                })
                .toList();
        return ResponseEntity.ok(new SessionPage(
                content, result.getNumber(), result.getSize(), result.getTotalElements()));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> end(
            @PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.SESSION_MANAGEMENT, PermissionAction.DELETE);
        Session session = sessions.findById(sessionId).filter(Session::isActive).orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        UUID actor = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        ending.end(List.of(session), actor, !session.getUserId().equals(actor), request.getRemoteAddr(), SessionController.userAgent(request));
        return ResponseEntity.ok(Map.of("ended", 1, "includesCurrent", session.getId().equals(currentSessionId)));
    }

    /**
     * Ends every active session of one user ({@code userId}), or of every user when it is omitted.
     * {@code includesCurrent} tells the caller whether their own session was among them, so the web app
     * signs them out.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> endAll(
            @RequestParam(required = false) UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.SESSION_MANAGEMENT, PermissionAction.DELETE);
        UUID actor = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        List<Session> targets = userId == null
                ? sessions.findByStatus(SessionStatus.ACTIVE)
                : sessions.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        boolean includesCurrent = targets.stream().anyMatch(s -> s.getId().equals(currentSessionId));
        int ended = 0;
        // One call per owner so "by admin" is only set for sessions that are not the caller's own.
        Map<UUID, List<Session>> byOwner =
                targets.stream().collect(Collectors.groupingBy(Session::getUserId));
        for (Map.Entry<UUID, List<Session>> entry : byOwner.entrySet()) {
            ended += ending.end(
                    entry.getValue(),
                    actor,
                    !entry.getKey().equals(actor),
                    request.getRemoteAddr(),
                    SessionController.userAgent(request));
        }
        return ResponseEntity.ok(Map.of("ended", ended, "includesCurrent", includesCurrent));
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }

    public record AdminSessionView(
            UUID id,
            UUID userId,
            String userName,
            String userPhone,
            String deviceDescription,
            Instant signedInAt,
            Instant lastActivityAt,
            boolean current,
            ClientSource clientType,
            String appVersion) {}

    public record SessionPage(List<AdminSessionView> content, int page, int size, long totalElements) {}
}
