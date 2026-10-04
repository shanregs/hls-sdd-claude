package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is deactivated or reactivated, for spec 003's Audit module to
 * subscribe to (research.md §2). This spec only ever publishes {@code active=false}
 * (deactivation) — {@code active=true} (reactivation) has no caller until spec 004 adds a
 * reactivation capability (spec.md FR-003); the event shape already supports it.
 */
public record AccountActivationChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean active, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public AccountActivationChanged {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public AccountActivationChanged(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean active) {
        this(eventId, occurredAt, actorUserId, affectedUserId, active, ClientContextHolder.current());
    }
}
