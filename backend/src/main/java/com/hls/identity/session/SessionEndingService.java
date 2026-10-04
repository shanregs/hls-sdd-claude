package com.hls.identity.session;

import com.hls.identity.activity.SessionEnded;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryPublisher;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends active sessions and records each one for the audit trail (spec 001 FR-015, FR-015a): a user
 * ending their own sessions, or System ending anyone's. Every ended session publishes a
 * {@link SessionEnded} event and a login-history entry, so a bulk end is as visible as a single one.
 */
@Service
public class SessionEndingService {

    private final SessionRepository sessions;
    private final AppUserRepository users;
    private final LoginHistoryPublisher loginHistory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SessionEndingService(
            SessionRepository sessions,
            AppUserRepository users,
            LoginHistoryPublisher loginHistory,
            ApplicationEventPublisher events,
            Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.loginHistory = loginHistory;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Ends the given sessions (already-ended ones are skipped) on behalf of {@code actorUserId}.
     *
     * @param byAdmin true when someone other than the owner is ending them (System), which changes the
     *     login-history entry
     * @return how many sessions were actually ended
     */
    @Transactional
    public int end(
            Collection<Session> toEnd, UUID actorUserId, boolean byAdmin, String remoteAddress, String userAgent) {
        List<Session> active = toEnd.stream().filter(Session::isActive).toList();
        Map<UUID, AppUser> owners = users.findAllById(
                        active.stream().map(Session::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (Session session : active) {
            session.end();
            sessions.save(session);
            AppUser owner = owners.get(session.getUserId());
            loginHistory.record(
                    session.getUserId(),
                    owner == null ? null : owner.getPhone(),
                    LoginMethod.PASSWORD,
                    byAdmin ? LoginEventType.SESSION_ENDED_BY_ADMIN : LoginEventType.SESSION_ENDED_BY_USER,
                    byAdmin ? "Session ended by System" : "Session ended from Sessions",
                    remoteAddress,
                    userAgent);
            events.publishEvent(new SessionEnded(
                    UUID.randomUUID(), clock.instant(), actorUserId, session.getUserId(), session.getId()));
        }
        return active.size();
    }
}
