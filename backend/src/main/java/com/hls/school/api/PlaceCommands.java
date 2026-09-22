package com.hls.school.api;

import com.hls.school.api.dto.PlaceView;
import java.util.UUID;

/**
 * Public write surface for Places (FR-001). Director/Admin-only — enforced
 * by {@code ZoneController} reading the caller's role from the JWT
 * directly, not by this interface.
 */
public interface PlaceCommands {

    /** FR-001/FR-002: adds a place under a Zone. No uniqueness check against existing name/pincode. */
    PlaceView addPlace(UUID zoneId, String name, String pincode, UUID actingUserId);
}
