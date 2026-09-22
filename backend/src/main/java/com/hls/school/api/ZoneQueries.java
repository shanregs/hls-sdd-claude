package com.hls.school.api;

import com.hls.school.api.dto.SchoolZoneAnswer;
import com.hls.school.api.dto.ZoneView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read surface for Zones and School-Zone assignment (FR-002/006/007).
 * {@code currentZoneForSchool} is the method other modules (specifically
 * `organization`, for its Manager-scoping constraint) depend on.
 */
public interface ZoneQueries {

    Optional<ZoneView> findById(UUID zoneId);

    /** FR-007: every School currently assigned to this Zone. Empty list, not an error, if none. */
    List<UUID> currentSchoolsForZone(UUID zoneId);

    /** FR-006: a School's current Zone, or UNASSIGNED — never an error for a never-assigned School. */
    SchoolZoneAnswer currentZoneForSchool(UUID schoolId);
}
