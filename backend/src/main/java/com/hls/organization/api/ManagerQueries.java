package com.hls.organization.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only Manager lookups for modules that depend on {@code organization} (for example teacher). */
public interface ManagerQueries {

    record ManagerRef(UUID id, UUID userId, String displayName, boolean active) {}

    /** The School's current Manager, if any. */
    Optional<ManagerRef> managerOfSchool(UUID schoolId);

    /** Current Managers keyed by School id, for the Schools that have one. */
    Map<UUID, ManagerRef> managersOfSchools(Collection<UUID> schoolIds);

    Optional<ManagerRef> managerByUserId(UUID userId);

    /** The Schools currently assigned to each of the given Managers. */
    Map<UUID, java.util.Set<UUID>> currentSchoolIds(Collection<UUID> managerIds);
}
