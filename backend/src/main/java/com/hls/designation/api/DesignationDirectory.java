package com.hls.designation.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read and assignment checks on the designation list for modules that attach designations to people. */
public interface DesignationDirectory {

    enum Kind {
        TEACHER,
        MANAGER
    }

    record DesignationInfo(UUID id, String name, Kind kind, boolean retired) {}

    /** The designation, if it exists: its kind and whether it is retired. */
    Optional<DesignationInfo> find(UUID designationId);

    /** The designation of the kind with this name (capitals and extra spaces ignored), for seeding and imports. */
    Optional<DesignationInfo> findByName(Kind kind, String name);

    /** Bulk form of {@link #find}; unknown ids are absent. One query. */
    Map<UUID, DesignationInfo> findAll(Collection<UUID> designationIds);

    /**
     * For writers: the designation must exist, be of {@code kind} and be active (otherwise an invalid-input
     * error naming the problem). Marks it as held, which fixes its kind.
     */
    DesignationInfo assign(UUID designationId, Kind kind);
}
