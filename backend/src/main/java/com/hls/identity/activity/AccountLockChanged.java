package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is locked (5th consecutive failed sign-in) or unlocked (a subsequent
 * successful sign-in), for spec 003's Audit module to subscribe to (research.md §2).
 * {@code actorUserId} is null for an automatic lockout — no human actor triggers it.
 */
public record AccountLockChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean locked) {}
