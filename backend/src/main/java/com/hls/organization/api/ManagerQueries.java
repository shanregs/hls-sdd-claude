package com.hls.organization.api;

import java.time.LocalDate;
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

    /** The active Managers currently assigned to the Zone (spec 023 notifies them); empty when it has none. */
    java.util.List<ManagerRef> managersOfZone(UUID zoneId);

    /** The Schools currently assigned to each of the given Managers. */
    Map<UUID, java.util.Set<UUID>> currentSchoolIds(Collection<UUID> managerIds);

    /** A Manager's employee id, joining and exit dates (spec 005a); any may be null. */
    record ManagerEmployment(UUID managerId, String employeeId, LocalDate joiningDate, LocalDate exitDate, boolean active) {}

    /** The id of the designation the Manager held on {@code date}: the latest row on or before it; empty when none. */
    Optional<UUID> designationOn(UUID managerId, LocalDate date);

    /** Bulk form of {@link #designationOn}; a Manager with none on the date is absent from the map. */
    Map<UUID, UUID> designationsOn(Collection<UUID> managerIds, LocalDate date);

    /** Employee id, joining and exit dates of the given Managers (unknown ids absent). */
    Map<UUID, ManagerEmployment> employment(Collection<UUID> managerIds);

    /** For each given designation, how many Managers hold it today (absent key means none). */
    Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds);
}
