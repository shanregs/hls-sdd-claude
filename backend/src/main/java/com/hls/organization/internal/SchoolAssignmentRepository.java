package com.hls.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolAssignmentRepository extends JpaRepository<SchoolAssignment, UUID> {

    Optional<SchoolAssignment> findBySchoolIdAndEffectiveToIsNull(UUID schoolId);

    List<SchoolAssignment> findBySchoolIdOrderByEffectiveFromAsc(UUID schoolId);

    List<SchoolAssignment> findByManagerIdAndEffectiveToIsNull(UUID managerId);

    List<SchoolAssignment> findByEffectiveToIsNull();

    /**
     * research.md §2: the conflict check. Ends the named assignment only if it
     * is still the current one; returns the number of rows affected (0 means
     * someone else already ended it — the caller must treat that as a conflict,
     * not retry silently).
     */
    @Modifying
    @Query("UPDATE SchoolAssignment a SET a.effectiveTo = :now WHERE a.id = :id AND a.effectiveTo IS NULL")
    int endIfStillCurrent(@Param("id") UUID id, @Param("now") Instant now);
}
