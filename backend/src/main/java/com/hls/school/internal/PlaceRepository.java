package com.hls.school.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately narrower than {@code JpaRepository}: FR-007 requires Places
 * be never deleted, so no {@code delete}/{@code deleteById} method is
 * exposed here at all. Mirrors {@code ZoneRepository}'s established pattern.
 */
public interface PlaceRepository extends Repository<Place, UUID> {

    Place save(Place place);

    List<Place> findByPincode(String pincode);

    List<Place> findByNameIgnoreCase(String name);

    List<Place> findByZoneId(UUID zoneId);
}
