package com.hls.organization.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * FR-005. A Zone's current coverage, composed at read time from
 * {@code organization}'s own Zone-Manager assignments and a live call to
 * {@code school.api.ZoneQueries.currentSchoolsForZone(...)} — never
 * persisted or cached (data-model.md).
 */
public record ZoneCoverage(UUID zoneId, List<UUID> managerIds, List<UUID> schoolIds) {
}
