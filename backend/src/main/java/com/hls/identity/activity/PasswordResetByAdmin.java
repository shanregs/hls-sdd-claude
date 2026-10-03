package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an Admin/System user sets another user's password, for spec 003's Audit module to
 * subscribe to. Never carries the password itself (spec 004 FR-006).
 */
public record PasswordResetByAdmin(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId) {}
