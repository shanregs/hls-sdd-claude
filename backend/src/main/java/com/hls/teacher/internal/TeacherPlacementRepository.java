package com.hls.teacher.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherPlacementRepository extends JpaRepository<TeacherPlacement, UUID> {

    List<TeacherPlacement> findByTeacherIdOrderByStartsOnDesc(UUID teacherId);

    List<TeacherPlacement> findByTeacherIdAndStatusOrderByStartsOnDesc(UUID teacherId, PlacementStatus status);

    /** ACTIVE placements in effect on {@code date} for the given Teachers. */
    @Query("""
            select p from TeacherPlacement p
            where p.teacherId in :teacherIds
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn <= :date
              and (p.endsOn is null or p.endsOn >= :date)
            """)
    List<TeacherPlacement> inEffectOn(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("date") LocalDate date);

    /** Scheduled (future) ACTIVE placements for the given Teachers. */
    @Query("""
            select p from TeacherPlacement p
            where p.teacherId in :teacherIds
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn > :date
            """)
    List<TeacherPlacement> scheduledAfter(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("date") LocalDate date);

    /** Ids of Teachers whose ACTIVE placement in effect on {@code date} is at one of the Schools. */
    @Query("""
            select distinct p.teacherId from TeacherPlacement p
            where p.schoolId in :schoolIds
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn <= :date
              and (p.endsOn is null or p.endsOn >= :date)
            """)
    List<UUID> teacherIdsAtSchoolsOn(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("date") LocalDate date);

    /** Teachers in effect on {@code date}, counted per School. */
    @Query("""
            select p.schoolId, count(distinct p.teacherId) from TeacherPlacement p
            where p.schoolId in :schoolIds
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn <= :date
              and (p.endsOn is null or p.endsOn >= :date)
            group by p.schoolId
            """)
    List<Object[]> countBySchoolOn(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("date") LocalDate date);

    /** True if any ACTIVE placement at the School is current or still to come. */
    @Query("""
            select count(p) > 0 from TeacherPlacement p
            where p.schoolId = :schoolId
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and (p.endsOn is null or p.endsOn >= :date)
            """)
    boolean existsCurrentOrFuture(@Param("schoolId") UUID schoolId, @Param("date") LocalDate date);

    /** ACTIVE placements of the Teachers that overlap {@code from..to} inclusive. */
    @Query("""
            select p from TeacherPlacement p
            where p.teacherId in :teacherIds
              and p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn <= :to
              and (p.endsOn is null or p.endsOn >= :from)
            order by p.startsOn
            """)
    List<TeacherPlacement> overlapping(
            @Param("teacherIds") Collection<UUID> teacherIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Ids of Teachers with an ACTIVE placement overlapping {@code from..to} inclusive. */
    @Query("""
            select distinct p.teacherId from TeacherPlacement p
            where p.status = com.hls.teacher.internal.PlacementStatus.ACTIVE
              and p.startsOn <= :to
              and (p.endsOn is null or p.endsOn >= :from)
            """)
    List<UUID> teacherIdsPlacedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
