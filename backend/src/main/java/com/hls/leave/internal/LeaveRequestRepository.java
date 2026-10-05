package com.hls.leave.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") UUID id);

    /** Live (pending or approved) requests of the Teacher that share a date with first..last. */
    @Query("""
            select r from LeaveRequest r
            where r.teacherId = :teacherId
              and r.status in (com.hls.leave.internal.LeaveStatus.PENDING, com.hls.leave.internal.LeaveStatus.APPROVED)
              and r.firstDate <= :last and r.lastDate >= :first
            order by r.firstDate
            """)
    List<LeaveRequest> findLiveOverlapping(
            @Param("teacherId") UUID teacherId, @Param("first") LocalDate first, @Param("last") LocalDate last);

    @Query("""
            select r from LeaveRequest r
            where r.teacherId = :teacherId and r.status in :statuses
            order by r.createdAt desc
            """)
    Page<LeaveRequest> findOwn(
            @Param("teacherId") UUID teacherId, @Param("statuses") Collection<LeaveStatus> statuses, Pageable pageable);

    /**
     * Requests visible to a supervisor. {@code orgWide} true ignores {@code teacherIds}; the optional
     * filters are passed as null when unused. The month filter is the month's first and last day.
     */
    @Query("""
            select r from LeaveRequest r
            where r.status in :statuses
              and (:orgWide = true or r.teacherId in :teacherIds)
              and (cast(:teacherId as uuid) is null or r.teacherId = :teacherId)
              and (cast(:schoolId as uuid) is null or r.schoolId = :schoolId)
              and r.firstDate <= :monthEnd and r.lastDate >= :monthStart
            order by r.createdAt desc
            """)
    Page<LeaveRequest> findInScope(
            @Param("statuses") Collection<LeaveStatus> statuses,
            @Param("orgWide") boolean orgWide,
            @Param("teacherIds") Collection<UUID> teacherIds,
            @Param("teacherId") UUID teacherId,
            @Param("schoolId") UUID schoolId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            Pageable pageable);

    @Query("""
            select count(r) from LeaveRequest r
            where r.status = com.hls.leave.internal.LeaveStatus.PENDING
              and (:orgWide = true or r.teacherId in :teacherIds)
            """)
    long countPendingInScope(@Param("orgWide") boolean orgWide, @Param("teacherIds") Collection<UUID> teacherIds);
}
