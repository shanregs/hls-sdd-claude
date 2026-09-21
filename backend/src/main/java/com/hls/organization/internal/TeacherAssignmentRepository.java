package com.hls.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {

    Optional<TeacherAssignment> findByTeacherIdAndEffectiveToIsNull(UUID teacherId);

    List<TeacherAssignment> findByTeacherIdOrderByEffectiveFromAsc(UUID teacherId);

    List<TeacherAssignment> findByManagerIdAndEffectiveToIsNull(UUID managerId);

    List<TeacherAssignment> findByEffectiveToIsNull();

    /** research.md §2 — same conflict-check pattern as {@code SchoolAssignmentRepository}. */
    @Modifying
    @Query("UPDATE TeacherAssignment a SET a.effectiveTo = :now WHERE a.id = :id AND a.effectiveTo IS NULL")
    int endIfStillCurrent(@Param("id") UUID id, @Param("now") Instant now);
}
