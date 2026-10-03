package com.hls.school.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookups of Zones, Places and Schools for other modules (Constitution Principle VII:
 * {@code organization} and {@code teacher} read school data only through this interface).
 */
public interface SchoolDirectory {

    record ZoneInfo(UUID id, String name) {}

    record PlaceInfo(UUID id, String name, String pinCode, UUID zoneId) {}

    record SchoolInfo(UUID id, String name, UUID placeId, UUID zoneId, boolean active) {}

    Optional<ZoneInfo> zone(UUID zoneId);

    /** Case-insensitive lookup by exact name (used by dev seeding). */
    Optional<ZoneInfo> zoneByName(String name);

    List<ZoneInfo> zones(Collection<UUID> zoneIds);

    Optional<PlaceInfo> place(UUID placeId);

    Optional<SchoolInfo> school(UUID schoolId);

    List<SchoolInfo> schools(Collection<UUID> schoolIds);

    List<SchoolInfo> schoolsInZone(UUID zoneId);
}
