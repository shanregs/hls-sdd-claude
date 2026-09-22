package com.hls.audit.api;

import com.hls.audit.api.dto.AuditEntryView;
import java.util.List;

/**
 * Public read surface of the {@code audit} module (FR-007). Backs
 * {@code AuditController}'s history endpoint and is available for any future
 * module that wants to check a record's history in-process.
 */
public interface AuditReader {

    /**
     * Every entry recorded for this {@code (entityType, entityId)} pair,
     * ordered oldest first by {@code sequenceNo} (FR-011). Returns an empty
     * list, not an error, for a pair with no recorded entries.
     */
    List<AuditEntryView> history(String entityType, String entityId);
}
