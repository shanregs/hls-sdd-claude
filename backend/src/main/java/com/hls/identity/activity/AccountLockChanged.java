package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is locked (5th consecutive failed sign-in) or unlocked (a subsequent
 * successful sign-in), for spec 003's Audit module to subscribe to (research.md §2).
 * {@code actorUserId} is null for an automatic lockout — no human actor triggers it.
 */
public record AccountLockChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean locked, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public AccountLockChanged {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public AccountLockChanged(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean locked) {
        this(eventId, occurredAt, actorUserId, affectedUserId, locked, ClientContextHolder.current());
    }
}
