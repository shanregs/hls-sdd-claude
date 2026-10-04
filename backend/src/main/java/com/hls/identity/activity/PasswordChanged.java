package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user changes their own password from Settings, for the Audit module's User
 * Activity list. Never carries either password.
 */
public record PasswordChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public PasswordChanged {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public PasswordChanged(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId) {
        this(eventId, occurredAt, actorUserId, affectedUserId, ClientContextHolder.current());
    }
}
