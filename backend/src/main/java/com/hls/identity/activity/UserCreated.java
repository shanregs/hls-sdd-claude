package com.hls.identity.activity;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.ClientContextHolder;
import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published when an Admin/System user creates an account, for spec 003's Audit module to subscribe
 * to. {@code actorUserId} is null when no human actor exists (bootstrap, dev seed).
 */
public record UserCreated(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID newUserId, Set<Role> roles, ClientContext clientContext) {

    /** A missing context means the web (spec 018): there is nothing client-specific to record. */
    public UserCreated {
        clientContext = clientContext == null ? ClientContext.web() : clientContext;
    }

    /**
     * Captures the current request's client context (source, app version, location) at publish
     * time, because the audit consumers run after the request on another thread (spec 018
     * research.md section 7).
     */
    public UserCreated(UUID eventId, Instant occurredAt, UUID actorUserId, UUID newUserId, Set<Role> roles) {
        this(eventId, occurredAt, actorUserId, newUserId, roles, ClientContextHolder.current());
    }
}
