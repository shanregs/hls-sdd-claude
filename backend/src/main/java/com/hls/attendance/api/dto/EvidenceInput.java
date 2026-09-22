package com.hls.attendance.api.dto;

import java.math.BigDecimal;

/** Optional supporting evidence for an attendance mark (FR-003). Every field is nullable. */
public record EvidenceInput(BigDecimal geoLat, BigDecimal geoLng, String photoUrl, String checkinCode) {
}
