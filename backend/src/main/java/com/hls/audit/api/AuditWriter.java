package com.hls.audit.api;

import com.hls.audit.api.dto.AuditRecordRequest;
import java.util.UUID;

/**
 * Public write surface of the {@code audit} module (FR-002, FR-003, FR-004,
 * FR-010). Any module creating, changing, or correcting a financial- or
 * attendance-affecting record calls {@link #record(AuditRecordRequest)}
 * in-process, inside its own existing transaction — the audited change and
 * its entry succeed or fail together (research.md §3).
 */
public interface AuditWriter {

    /**
     * Records one entry. Returns the new entry's id. Throws
     * {@link IllegalArgumentException} if {@code request.actorUserId()} is
     * null — an unattributable change is refused, never recorded (FR-010).
     */
    UUID record(AuditRecordRequest request);
}
