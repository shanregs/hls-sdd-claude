package com.hls.school.api;

import java.util.UUID;

/**
 * Creates a School on behalf of another module (spec 023 creates one when a prospect is won). It goes through the same
 * rules and audit as a School added on the Schools screen.
 */
public interface SchoolRegistry {

    /** The fields a new School needs besides its Place. */
    record NewSchool(String name, String address, String contactPerson, String contactPhone, String billingContact) {}

    /** The School already in the Place with this name (ignoring case), so the caller can link instead of duplicating. */
    java.util.Optional<UUID> findByNameInPlace(UUID placeId, String name);

    UUID create(UUID actor, UUID placeId, NewSchool school);
}
