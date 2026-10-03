package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is deactivated or reactivated, for spec 003's Audit module to
 * subscribe to (research.md §2). This spec only ever publishes {@code active=false}
 * (deactivation) — {@code active=true} (reactivation) has no caller until spec 004 adds a
 * reactivation capability (spec.md FR-003); the event shape already supports it.
 */
public record AccountActivationChanged(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, boolean active) {}
