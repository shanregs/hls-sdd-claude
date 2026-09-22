package com.hls.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side shape of one recorded audit entry, returned by
 * {@code AuditReader.history(...)} (FR-007). {@code sequenceNo} is the
 * definitive ordering key (research.md §5) — {@code occurredAt} is for
 * display only.
 */
public record AuditEntryView(
        UUID id,
        long sequenceNo,
        String sourceModule,
        String entityType,
        String entityId,
        AuditAction action,
        String summary,
        String beforeValue,
        String afterValue,
        UUID actorUserId,
        String actorRole,
        Instant occurredAt,
        String requestId) {
}
