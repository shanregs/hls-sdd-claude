package com.hls.identity.session;

import com.hls.identity.activity.SessionEnded;
import com.hls.identity.clientcontext.ClientSource;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryPublisher;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service session management (FR-015, User Story 5): a signed-in user may list and end only
 * their own sessions, never another user's (contracts/auth-api.md).
 */
@RestController
public class SessionController {

    private final SessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;
    private final LoginHistoryPublisher loginHistoryPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SessionController(
            SessionRepository sessionRepository,
            AppUserRepository appUserRepository,
            LoginHistoryPublisher loginHistoryPublisher,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.loginHistoryPublisher = loginHistoryPublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @GetMapping("/api/v1/me/sessions")
    public ResponseEntity<List<SessionView>> listSessions(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        List<SessionView> views = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).stream()
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
    @Transactional
    public ResponseEntity<Void> endSession(
            @PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        session.end();
        sessionRepository.save(session);

        String phone = appUserRepository.findById(userId).map(u -> u.getPhone()).orElse(null);
        loginHistoryPublisher.record(
                userId,
                phone,
                LoginMethod.PASSWORD,
                LoginEventType.SESSION_ENDED_BY_USER,
                "Session ended from Profile",
                httpRequest.getRemoteAddr(),
                userAgent(httpRequest));
        eventPublisher.publishEvent(
                new SessionEnded(UUID.randomUUID(), clock.instant(), userId, userId, sessionId));

        return ResponseEntity.noContent().build();
    }

    private static String userAgent(HttpServletRequest request) {
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
