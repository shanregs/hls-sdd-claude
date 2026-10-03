package com.hls.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session lifecycle (data-model.md's "Session"/"RenewalCredential"). {@link #createSession}
 * supports sign-in (User Stories 1 and 2); {@link #renew} adds rotation and reuse-detection
 * (User Story 3, FR-009/FR-010).
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final RenewalCredentialRepository renewalCredentialRepository;
    private final RenewalCredentialHasher hasher;
    private final Clock clock;
    private final Duration renewalTtl;

    public SessionService(
            SessionRepository sessionRepository,
            RenewalCredentialRepository renewalCredentialRepository,
            RenewalCredentialHasher hasher,
            Clock clock,
            @Value("${hls.session.renewal-ttl-days:14}") long renewalTtlDays) {
        this.sessionRepository = sessionRepository;
        this.renewalCredentialRepository = renewalCredentialRepository;
        this.hasher = hasher;
        this.clock = clock;
        this.renewalTtl = Duration.ofDays(renewalTtlDays);
    }

    /**
     * Creates a new session for a just-authenticated user and its first renewal credential.
     *
     * @return the created session and the <em>plain</em> renewal credential value (set as the
     *     cookie by the caller; never persisted or returned again).
     */
    @Transactional
    public CreatedSession createSession(UUID userId, String deviceDescription) {
        Instant now = clock.instant();
        Session session = sessionRepository.save(new Session(userId, deviceDescription, now));
        String plainCredential = hasher.generatePlainValue();
        renewalCredentialRepository.save(
                new RenewalCredential(session.getId(), hasher.hash(plainCredential), now));
        return new CreatedSession(session, plainCredential);
    }

    /** Ends one session immediately (FR-014's logout, FR-015's "end a session from Profile"). */
    @Transactional
    public void endSession(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.end();
            sessionRepository.save(session);
        });
    }

    /** Revokes every active session of a deactivated user immediately (FR-018, data-model.md:
     * {@code REVOKED} = "reuse-detection or deactivation"). */
    @Transactional
    public void revokeAllSessionsForUser(UUID userId) {
        sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).forEach(session -> {
            session.revoke();
            sessionRepository.save(session);
        });
    }

    /**
     * Rotates a renewal credential (FR-009): looks it up by hash, and either issues the next
     * credential in its chain, refuses because it was already used (cascade-revoking that session
     * and every descendant credential's session, FR-010), or refuses because more than the
     * configured renewal period has passed since the original sign-in (FR-009).
     */
    @Transactional
    public RenewResult renew(String renewalCredentialPlainValue) {
        Instant now = clock.instant();
        Optional<RenewalCredential> maybeCredential =
                renewalCredentialRepository.findByCredentialHash(hasher.hash(renewalCredentialPlainValue));
        if (maybeCredential.isEmpty()) {
            return RenewResult.invalid();
        }
        RenewalCredential credential = maybeCredential.get();
        Session session = sessionRepository.findById(credential.getSessionId()).orElse(null);
        if (session == null || !session.isActive()) {
            return RenewResult.invalid();
        }
        if (credential.isUsed()) {
            UUID userId = session.getUserId();
            revokeChain(credential);
            return RenewResult.reuseDetected(userId);
        }
        if (now.isAfter(session.getSignedInAt().plus(renewalTtl))) {
            return RenewResult.expired();
        }

        String newPlainCredential = hasher.generatePlainValue();
        RenewalCredential next = renewalCredentialRepository.save(
                new RenewalCredential(session.getId(), hasher.hash(newPlainCredential), now));
        credential.markUsed(now, next.getId());
        renewalCredentialRepository.save(credential);
        session.touch(now);
        sessionRepository.save(session);
        return RenewResult.success(session, newPlainCredential);
    }

    /** Revokes the session of {@code start} and of every credential reachable by following
     * {@code superseded_by} forward from it (the full "descended from it" chain, FR-010). */
    private void revokeChain(RenewalCredential start) {
        Set<UUID> revokedSessionIds = new HashSet<>();
        RenewalCredential current = start;
        while (current != null) {
            if (revokedSessionIds.add(current.getSessionId())) {
                sessionRepository.findById(current.getSessionId()).ifPresent(session -> {
                    session.revoke();
                    sessionRepository.save(session);
                });
            }
            UUID nextId = current.getSupersededBy();
            current = nextId != null ? renewalCredentialRepository.findById(nextId).orElse(null) : null;
        }
    }

    public record CreatedSession(Session session, String plainRenewalCredential) {}

    /** Outcome of {@link #renew}. */
    public record RenewResult(Outcome outcome, Session session, String plainRenewalCredential, UUID userId) {

        public enum Outcome {
            SUCCESS,
            REUSE_DETECTED,
            EXPIRED,
            INVALID
        }

        static RenewResult success(Session session, String plainRenewalCredential) {
            return new RenewResult(Outcome.SUCCESS, session, plainRenewalCredential, session.getUserId());
        }

        static RenewResult reuseDetected(UUID userId) {
            return new RenewResult(Outcome.REUSE_DETECTED, null, null, userId);
        }

        static RenewResult expired() {
            return new RenewResult(Outcome.EXPIRED, null, null, null);
        }

        static RenewResult invalid() {
            return new RenewResult(Outcome.INVALID, null, null, null);
        }
    }
}
