package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user changes their own password from Settings, for the Audit module's User
 * Activity list. Never carries either password.
 */
public record PasswordChanged(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId) {}
