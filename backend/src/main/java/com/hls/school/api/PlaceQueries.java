package com.hls.school.api;

import com.hls.school.api.dto.PlaceView;
import java.util.List;
import java.util.UUID;

/**
 * Public read surface for Places (FR-003/FR-004/FR-005/FR-006). Every
 * method returns an empty list, never null or an error, when nothing
 * matches.
 */
public interface PlaceQueries {

    /** FR-003: every place recorded with this PIN code, across all Zones. */
    List<PlaceView> findByPincode(String pincode);

    /** FR-004: every place recorded with this name (case-insensitive), across all Zones. */
    List<PlaceView> findByName(String name);

    /** FR-006: every place currently recorded under this Zone. */
    List<PlaceView> findByZoneId(UUID zoneId);
}
