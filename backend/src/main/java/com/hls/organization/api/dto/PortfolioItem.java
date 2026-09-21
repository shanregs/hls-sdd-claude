package com.hls.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/** FR-008: one row per School/Teacher currently accountable to a given Manager. */
public record PortfolioItem(ItemType itemType, UUID itemId, Instant since) {
}
