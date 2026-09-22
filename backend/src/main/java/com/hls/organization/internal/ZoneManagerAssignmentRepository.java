package com.hls.organization.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneManagerAssignmentRepository extends JpaRepository<ZoneManagerAssignment, UUID> {

    Optional<ZoneManagerAssignment> findByZoneIdAndManagerIdAndEffectiveToIsNull(UUID zoneId, UUID managerId);

    List<ZoneManagerAssignment> findByZoneIdAndEffectiveToIsNull(UUID zoneId);

    /**
     * research.md §2 (mirrors {@code SchoolAssignmentRepository}'s own conflict
     * check): ends the named assignment only if it is still current; returns
     * the number of rows affected (0 means someone else already ended it).
     */
    @Modifying
    @Query("UPDATE ZoneManagerAssignment a SET a.effectiveTo = :now WHERE a.id = :id AND a.effectiveTo IS NULL")
    int endIfStillCurrent(@Param("id") UUID id, @Param("now") Instant now);
}
