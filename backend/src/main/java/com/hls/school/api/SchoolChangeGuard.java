package com.hls.school.api;

import java.util.UUID;

/**
 * Implemented by dependent modules to veto moving a School to a Place in {@code newZoneId} (for
 * example when its Manager does not cover that Zone); throw {@link ConflictException} to refuse.
 */
public interface SchoolChangeGuard {

    void checkPlaceChange(UUID schoolId, UUID newZoneId);
}
