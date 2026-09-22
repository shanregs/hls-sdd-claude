package com.hls.school.api;

import com.hls.school.api.dto.BulkImportResponse;
import com.hls.school.api.dto.BulkPlaceRow;
import com.hls.school.api.dto.PlaceView;
import java.util.List;
import java.util.UUID;

/**
 * Public write surface for Places (FR-001). Director/Admin-only — enforced
 * by {@code ZoneController} reading the caller's role from the JWT
 * directly, not by this interface.
 */
public interface PlaceCommands {

    /** FR-001/FR-002: adds a place under a Zone. No uniqueness check against existing name/pincode. */
    PlaceView addPlace(UUID zoneId, String name, String pincode, UUID actingUserId);

    /**
     * specs/010-place-bulk-import FR-001-006: adds many places in one call.
     * A row failing validation is reported as failed, never blocking other
     * valid rows (FR-002/003/004); the whole batch is rejected up front
     * (throws {@link BulkImportBatchException}) if empty or over the max
     * row count (FR-005/006), before any row is processed.
     */
    BulkImportResponse bulkImportPlaces(List<BulkPlaceRow> rows, UUID actingUserId);
}
