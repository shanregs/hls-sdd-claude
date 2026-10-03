package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a password reset code is issued, for spec 003's Audit module to subscribe to
 * (research.md §2) — {@code identity} does not depend on spec 003, mirroring
 * {@code LoginHistoryRecorded}'s existing pattern (spec 001).
 */
public record PasswordResetRequested(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId) {}
