package com.hls.organization.api;

import com.hls.organization.api.dto.CurrentAssignment;
import com.hls.organization.api.dto.ZoneManagerAssignmentView;
import java.util.UUID;

/**
 * Public write surface (FR-001/002/004/010). Every method here is Director/Admin-
 * only — enforced by {@code OrganizationController} reading the caller's role
 * from the JWT directly (research.md §6), not by this interface.
 */
public interface AccountabilityCommands {

    /**
     * Assigns {@code managerId} as the accountable Manager for {@code schoolId}.
     * If {@code endsAssignmentId} is null, this must be the School's first-ever
     * assignment or its current one must already belong to {@code managerId}
     * (FR-010's no-op case). If non-null, it names the current assignment row to
     * end — a conflict is raised (see {@code AssignmentConflictException}) if
     * that row was already ended by someone else (FR-011).
     */
    CurrentAssignment assignSchoolManager(UUID schoolId, UUID managerId, UUID endsAssignmentId, UUID actingUserId);

    CurrentAssignment assignTeacherManager(UUID teacherId, UUID managerId, UUID endsAssignmentId, UUID actingUserId);

    /** Edge Case 1: ends the named assignment with no replacement, leaving the item UNASSIGNED. */
    void endSchoolAssignment(UUID assignmentId, UUID actingUserId);

    void endTeacherAssignment(UUID assignmentId, UUID actingUserId);

    /**
     * specs/006-zone-scoping FR-001/FR-002: assigns {@code managerId} to
     * cover {@code zoneId}. Repeatable for different managers — a Zone may
     * have more than one current covering Manager. A repeat call naming the
     * same (zoneId, managerId) pair already current is a no-op. Throws
     * {@link ZoneNotFoundException} if {@code zoneId} does not correspond to
     * an existing Zone (validated live through {@code school.api.ZoneQueries}).
     */
    ZoneManagerAssignmentView assignManagerToZone(UUID zoneId, UUID managerId, UUID actingUserId);

    /** FR-001: ends the named Zone-Manager coverage assignment. */
    void removeManagerFromZone(UUID assignmentId, UUID actingUserId);
}
