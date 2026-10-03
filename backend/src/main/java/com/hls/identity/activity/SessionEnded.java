package com.hls.identity.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a session is ended (self-service or otherwise), for spec 003's Audit module to
 * subscribe to (research.md §2) — {@code identity} does not depend on spec 003.
 */
public record SessionEnded(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, UUID sessionId) {}
