package com.hls.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-009. {@code lastEndedAt} is null if this item has never been assigned at
 * all — though see {@code AccountabilityService}'s class comment: without a
 * Teacher/School master table to enumerate against, this implementation can
 * only surface identifiers with at least one row in the assignment tables
 * (i.e. ended-without-replacement), the same narrowing FR-012 already accepts.
 */
public record UnassignedItem(ItemType itemType, UUID itemId, Instant lastEndedAt) {
}
