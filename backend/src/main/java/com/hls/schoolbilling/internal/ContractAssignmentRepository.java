package com.hls.schoolbilling.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The queries of the old TeacherPlacementRepository, over assignments. */
public interface ContractAssignmentRepository extends JpaRepository<ContractAssignment, UUID> {

    List<ContractAssignment> findByTeacherIdOrderByStartsOnDesc(UUID teacherId);

    List<ContractAssignment> findByTeacherIdAndStatusOrderByStartsOnDesc(UUID teacherId, AssignmentStatus status);

    /** ACTIVE assignments in effect on {@code date} for the given Teachers. */
    @Query("""
            select a from ContractAssignment a
            where a.teacherId in :teacherIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :date
              and (a.endsOn is null or a.endsOn >= :date)
            """)
    List<ContractAssignment> inEffectOn(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("date") LocalDate date);

    /** Scheduled (future) ACTIVE assignments for the given Teachers. */
    @Query("""
            select a from ContractAssignment a
            where a.teacherId in :teacherIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn > :date
            """)
    List<ContractAssignment> scheduledAfter(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("date") LocalDate date);

    /** Ids of Teachers whose ACTIVE assignment in effect on {@code date} is at one of the Schools. */
    @Query("""
            select distinct a.teacherId from ContractAssignment a
            where a.schoolId in :schoolIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :date
              and (a.endsOn is null or a.endsOn >= :date)
            """)
    List<UUID> teacherIdsAtSchoolsOn(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("date") LocalDate date);

    /** Teachers in effect on {@code date}, counted per School. */
    @Query("""
            select a.schoolId, count(distinct a.teacherId) from ContractAssignment a
            where a.schoolId in :schoolIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :date
              and (a.endsOn is null or a.endsOn >= :date)
            group by a.schoolId
            """)
    List<Object[]> countBySchoolOn(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("date") LocalDate date);

    /** True if any ACTIVE assignment at the School is current or still to come. */
    @Query("""
            select count(a) > 0 from ContractAssignment a
            where a.schoolId = :schoolId
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and (a.endsOn is null or a.endsOn >= :date)
            """)
    boolean existsCurrentOrFuture(@Param("schoolId") UUID schoolId, @Param("date") LocalDate date);

    /** ACTIVE assignments of the Teachers that overlap {@code from..to} inclusive. */
    @Query("""
            select a from ContractAssignment a
            where a.teacherId in :teacherIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :to
              and (a.endsOn is null or a.endsOn >= :from)
            order by a.startsOn
            """)
    List<ContractAssignment> overlapping(
            @Param("teacherIds") Collection<UUID> teacherIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Ids of Teachers with an ACTIVE assignment overlapping {@code from..to} inclusive. */
    @Query("""
            select distinct a.teacherId from ContractAssignment a
            where a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :to
              and (a.endsOn is null or a.endsOn >= :from)
            """)
    List<UUID> teacherIdsAssignedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** ACTIVE assignments at the School overlapping {@code from..to}, oldest first. */
    @Query("""
            select a from ContractAssignment a
            where a.schoolId = :schoolId
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :to
              and (a.endsOn is null or a.endsOn >= :from)
            order by a.startsOn
            """)
    List<ContractAssignment> atSchoolOverlapping(
            @Param("schoolId") UUID schoolId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** ACTIVE assignments in effect on {@code date} at any of the Schools. */
    @Query("""
            select a from ContractAssignment a
            where a.schoolId in :schoolIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :date
              and (a.endsOn is null or a.endsOn >= :date)
            """)
    List<ContractAssignment> atSchoolsOn(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("date") LocalDate date);

    /** ACTIVE assignments mapped to the positions that overlap {@code from..to}. */
    @Query("""
            select a from ContractAssignment a
            where a.positionId in :positionIds
              and a.status = com.hls.schoolbilling.internal.AssignmentStatus.ACTIVE
              and a.startsOn <= :to
              and (a.endsOn is null or a.endsOn >= :from)
            """)
    List<ContractAssignment> onPositionsOverlapping(
            @Param("positionIds") Collection<UUID> positionIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
