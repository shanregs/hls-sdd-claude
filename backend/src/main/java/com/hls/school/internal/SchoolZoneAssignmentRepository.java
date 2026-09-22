package com.hls.school.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolZoneAssignmentRepository extends JpaRepository<SchoolZoneAssignment, UUID> {

    Optional<SchoolZoneAssignment> findBySchoolIdAndEffectiveToIsNull(UUID schoolId);

    List<SchoolZoneAssignment> findBySchoolIdOrderByEffectiveFromAsc(UUID schoolId);

    List<SchoolZoneAssignment> findByZoneIdAndEffectiveToIsNull(UUID zoneId);

    /**
     * research.md §1: the conflict check, identical technique to
     * {@code organization.internal.SchoolAssignmentRepository.endIfStillCurrent}.
     * Ends the named assignment only if it is still current; returns the
     * number of rows affected (0 means someone else already ended it first —
     * the caller must treat that as a conflict, not retry silently).
     */
    @Modifying
    @Query("UPDATE SchoolZoneAssignment a SET a.effectiveTo = :now WHERE a.id = :id AND a.effectiveTo IS NULL")
    int endIfStillCurrent(@Param("id") UUID id, @Param("now") Instant now);
}
