package com.hls.school.api.dto;

import java.util.UUID;

/** FR-001. One row of a bulk-import batch — same fields as the single-add request. */
public record BulkPlaceRow(UUID zoneId, String name, String pincode) {
}
