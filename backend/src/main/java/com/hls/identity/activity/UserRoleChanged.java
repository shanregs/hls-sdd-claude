package com.hls.identity.activity;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.UUID;

/** Published once per role added ({@code added=true}) or removed, for spec 003's Audit module. */
public record UserRoleChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, Role role, boolean added) {}
