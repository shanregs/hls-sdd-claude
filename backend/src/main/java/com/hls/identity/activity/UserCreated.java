package com.hls.identity.activity;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published when an Admin/System user creates an account, for spec 003's Audit module to subscribe
 * to. {@code actorUserId} is null when no human actor exists (bootstrap, dev seed).
 */
public record UserCreated(UUID eventId, Instant occurredAt, UUID actorUserId, UUID newUserId, Set<Role> roles) {}
