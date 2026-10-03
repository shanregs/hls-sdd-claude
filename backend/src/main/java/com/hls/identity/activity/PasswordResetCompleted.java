package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a password reset is completed, for spec 003's Audit module to subscribe to
 * (research.md §2) — {@code identity} does not depend on spec 003.
 */
public record PasswordResetCompleted(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId) {}
