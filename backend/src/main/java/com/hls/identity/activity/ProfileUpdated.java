package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user edits their own profile from Settings. {@code changedFields} names what
 * changed (for example "display name, email"), never the values.
 */
public record ProfileUpdated(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, String changedFields, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public ProfileUpdated {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public ProfileUpdated(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, String changedFields) {
        this(eventId, occurredAt, actorUserId, affectedUserId, changedFields, ClientContextHolder.current());
    }
}
