package com.hls.organization.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneManagerAssignmentRepository extends JpaRepository<ZoneManagerAssignment, UUID> {

    List<ZoneManagerAssignment> findByManagerIdAndEndsOnIsNull(UUID managerId);

    List<ZoneManagerAssignment> findByManagerIdInAndEndsOnIsNull(Collection<UUID> managerIds);

    List<ZoneManagerAssignment> findByZoneIdInAndEndsOnIsNull(Collection<UUID> zoneIds);

    boolean existsByZoneIdAndEndsOnIsNull(UUID zoneId);

    boolean existsByZoneIdAndManagerIdAndEndsOnIsNull(UUID zoneId, UUID managerId);

    Optional<ZoneManagerAssignment> findByZoneIdAndManagerIdAndEndsOnIsNull(UUID zoneId, UUID managerId);

    List<ZoneManagerAssignment> findByManagerIdOrderByStartsOnDesc(UUID managerId);
}
