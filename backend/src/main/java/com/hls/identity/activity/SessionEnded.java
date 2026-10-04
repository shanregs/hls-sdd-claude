package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a session is ended (self-service or otherwise), for spec 003's Audit module to
 * subscribe to (research.md §2) — {@code identity} does not depend on spec 003.
 */
public record SessionEnded(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, UUID sessionId, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public SessionEnded {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public SessionEnded(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, UUID sessionId) {
        this(eventId, occurredAt, actorUserId, affectedUserId, sessionId, ClientContextHolder.current());
    }
}
