package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.UUID;

/** Published once per role added ({@code added=true}) or removed, for spec 003's Audit module. */
public record UserRoleChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, Role role, boolean added, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public UserRoleChanged {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public UserRoleChanged(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, Role role, boolean added) {
        this(eventId, occurredAt, actorUserId, affectedUserId, role, added, ClientContextHolder.current());
    }
}
