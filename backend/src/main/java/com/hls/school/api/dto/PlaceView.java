package com.hls.school.api.dto;

import java.util.UUID;

/** FR-001-006. The read-side shape of one Place. */
public record PlaceView(UUID id, UUID zoneId, String name, String pincode) {
}
