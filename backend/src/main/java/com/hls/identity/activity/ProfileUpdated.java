package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user edits their own profile from Settings. {@code changedFields} names what
 * changed (for example "display name, email"), never the values.
 */
public record ProfileUpdated(
        UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, String changedFields) {}
