package com.hls.audit.logs;

import java.time.Instant;
import java.util.UUID;

/** The unified Audit Log Entry projection (data-model.md), a query-time read model — not a table. */
public record AuditLogEntryView(
        Instant occurredAt,
        String type,
        UUID actorUserId,
        String summary,
        String source,
        String appVersion,
        com.hls.audit.support.LocationView location,
        Boolean deviceRooted) {

    /**
     * A row with no client information (change history has none): source, app version, location and
     * the rooted flag are null.
     */
    public AuditLogEntryView(Instant occurredAt, String type, UUID actorUserId, String summary) {
        this(occurredAt, type, actorUserId, summary, null, null, null, null);
    }
}
