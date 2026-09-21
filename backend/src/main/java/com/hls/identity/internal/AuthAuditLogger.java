package com.hls.identity.internal;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * FR-013: one method per event type, each writing exactly one append-only
 * {@link AuthAuditEntry} row (Constitution Principle I). No method here ever
 * updates or deletes a previous row.
 */
@Component
public class AuthAuditLogger {

    private final AuthAuditEntryRepository repository;
    private final Clock clock;

    public AuthAuditLogger(AuthAuditEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void loginSuccess(UUID userId, Set<Role> roles, String requestId) {
        write(userId, null, AuditAction.LOGIN_SUCCESS, rolesToString(roles), requestId);
    }

    /** FR-015: {@code attemptedIdentifier} is recorded even when it never resolved to a user. */
    public void loginFailure(UUID userId, String attemptedIdentifier, String requestId) {
        write(userId, attemptedIdentifier, AuditAction.LOGIN_FAILURE, null, requestId);
    }

    public void accessDenied(UUID userId, String roleAtTime, String requestId) {
        write(userId, null, AuditAction.ACCESS_DENIED, roleAtTime, requestId);
    }

    public void sessionRevoked(UUID userId, String requestId) {
        write(userId, null, AuditAction.SESSION_REVOKED, null, requestId);
    }

    public void accountLocked(UUID userId, String requestId) {
        write(userId, null, AuditAction.ACCOUNT_LOCKED, null, requestId);
    }

    public void accountUnlocked(UUID userId, String requestId) {
        write(userId, null, AuditAction.ACCOUNT_UNLOCKED, null, requestId);
    }

    public void passwordResetRequested(UUID userId, String requestId) {
        write(userId, null, AuditAction.PASSWORD_RESET_REQUESTED, null, requestId);
    }

    public void passwordResetCompleted(UUID userId, String requestId) {
        write(userId, null, AuditAction.PASSWORD_RESET_COMPLETED, null, requestId);
    }

    private void write(UUID actorUserId, String attemptedIdentifier, AuditAction action, String roleAtTime, String requestId) {
        repository.save(new AuthAuditEntry(
                UUID.randomUUID(), actorUserId, attemptedIdentifier, action, roleAtTime, clock.instant(), requestId));
    }

    private String rolesToString(Set<Role> roles) {
        return roles == null ? null : roles.toString();
    }
}
