package com.hls.school.api;

import com.hls.school.api.dto.CurrentSchoolZoneAssignment;
import java.util.UUID;

/**
 * Public write surface for Zones and School-Zone assignment (FR-001/003/004).
 * Every method here is Director/Admin-only — enforced by {@code ZoneController}
 * reading the caller's role from the JWT directly, not by this interface.
 */
public interface ZoneCommands {

    UUID createZone(String name, UUID actingUserId);

    /**
     * Assigns {@code zoneId} as {@code schoolId}'s current Zone. If
     * {@code endsAssignmentId} is null, this must be the School's first-ever
     * assignment — a School that already has a current Zone is rejected
     * (see {@link ZoneAssignmentConflictException}), never silently
     * overwritten. If non-null, it names the current assignment row to end —
     * a conflict is raised if that row was already ended by someone else
     * (FR-005).
     */
    CurrentSchoolZoneAssignment assignSchoolToZone(UUID schoolId, UUID zoneId, UUID endsAssignmentId, UUID actingUserId);
}
