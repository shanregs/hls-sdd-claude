package com.hls.transfer.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MovementRequestRepository extends JpaRepository<MovementRequest, UUID> {

    List<MovementRequest> findByStatusIn(Collection<String> statuses);

    List<MovementRequest> findByStatus(String status);

    List<MovementRequest> findByStatusAndEffectiveOnBetween(String status, java.time.LocalDate from, java.time.LocalDate to);

    @Query("""
            select r from MovementRequest r
            where r.teacherId = :teacherId or r.replacementTeacherId = :teacherId
            order by r.requestedAt desc
            """)
    List<MovementRequest> involving(@Param("teacherId") UUID teacherId);

    @Query("""
            select r from MovementRequest r
            where r.fromSchoolId = :schoolId or r.toSchoolId = :schoolId or r.outgoingDestinationSchoolId = :schoolId
            order by r.requestedAt desc
            """)
    List<MovementRequest> atSchool(@Param("schoolId") UUID schoolId);
}
