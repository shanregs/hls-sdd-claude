package com.hls.audit.api.dto;

import java.util.UUID;

/**
 * Write-side shape passed to {@code AuditWriter.record(...)} (FR-002/FR-003).
 * Deliberately has no {@code id}, {@code sequenceNo}, or {@code occurredAt}
 * field — {@code audit} assigns all three itself and never trusts a caller-
 * supplied value for any of them (research.md §5; FR-010's attribution
 * guarantee would otherwise be spoofable by a caller-supplied identity too,
 * which is why {@code actorUserId} is required rather than inferred).
 *
 * <p>{@code entityType}/{@code entityId}/{@code beforeValue}/{@code afterValue}
 * are opaque to {@code audit} — it stores and returns them verbatim, never
 * parses or validates their internal structure (research.md §2).
 */
public record AuditRecordRequest(
        String sourceModule,
        String entityType,
        String entityId,
        AuditAction action,
        String summary,
        String beforeValue,
        String afterValue,
        UUID actorUserId,
        String actorRole,
        String requestId) {
}
