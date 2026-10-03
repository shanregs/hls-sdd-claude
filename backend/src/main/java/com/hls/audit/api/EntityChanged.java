package com.hls.audit.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One field-level change to a governed record, published by the master-data modules ({@code
 * school}, {@code organization}, {@code teacher}) for {@code audit} to append to Change History.
 * {@code audit} depends only on {@code identity}; publishers depend on this record, never the other
 * way round (spec 005 research.md section 1).
 */
public record EntityChanged(
        UUID eventId,
        Instant occurredAt,
        UUID actorUserId,
        String entityType,
        String entityId,
        String field,
        String beforeValue,
        String afterValue) {}
