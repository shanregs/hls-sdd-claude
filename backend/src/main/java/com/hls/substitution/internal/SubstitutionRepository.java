package com.hls.substitution.internal;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

public interface SubstitutionRepository extends JpaRepository<SubstitutionRequirement, UUID> {

    Optional<SubstitutionRequirement> findByTeacherIdAndDutyDate(UUID teacherId, LocalDate dutyDate);

    /** Requirements of the absent Teacher on or after the date that are not cancelled (spec 027 relocation). */
    @Query("""
            select r from SubstitutionRequirement r
            where r.teacherId = :teacherId and r.dutyDate >= :from
              and r.status <> com.hls.substitution.internal.RequirementStatus.CANCELLED
            order by r.dutyDate
            """)
    List<SubstitutionRequirement> liveFrom(@Param("teacherId") UUID teacherId, @Param("from") LocalDate from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SubstitutionRequirement r where r.id = :id")
    Optional<SubstitutionRequirement> findByIdForUpdate(@Param("id") UUID id);

    /**
     * The list: {@code allSchools} true ignores {@code schoolIds}; the optional filters are switched by their flags so
     * no null parameter reaches the database.
     */
    @Query("""
            select r from SubstitutionRequirement r
            where (:allSchools = true or r.schoolId in :scopeSchoolIds)
              and (:filterSchool = false or r.schoolId = :schoolId)
              and r.status in :statuses
              and r.dutyDate between :from and :to
            order by r.dutyDate desc, r.raisedAt desc, r.id
            """)
    Page<SubstitutionRequirement> search(
            @Param("allSchools") boolean allSchools,
            @Param("scopeSchoolIds") Collection<UUID> scopeSchoolIds,
            @Param("filterSchool") boolean filterSchool,
            @Param("schoolId") UUID schoolId,
            @Param("statuses") Collection<RequirementStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** Live credits: assigned requirements of the given substitutes in the range. */
    @Query("""
            select r from SubstitutionRequirement r
            where r.substituteTeacherId in :teacherIds
              and r.status = com.hls.substitution.internal.RequirementStatus.ASSIGNED
              and r.dutyDate between :from and :to
            order by r.dutyDate, r.id
            """)
    List<SubstitutionRequirement> credits(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** All assigned requirements on a date for the given substitutes (to add up how much of the day is covered). */
    @Query("""
            select r from SubstitutionRequirement r
            where r.substituteTeacherId in :teacherIds
              and r.status = com.hls.substitution.internal.RequirementStatus.ASSIGNED
              and r.dutyDate = :date
            """)
    List<SubstitutionRequirement> assignedOn(@Param("teacherIds") Collection<UUID> teacherIds, @Param("date") LocalDate date);

    @Query("""
            select coalesce(sum(r.dayValue), 0) from SubstitutionRequirement r
            where r.substituteTeacherId = :teacherId
              and r.status = com.hls.substitution.internal.RequirementStatus.ASSIGNED
              and r.dutyDate = :date and r.id <> :excludeId
            """)
    BigDecimal coveredOn(
            @Param("teacherId") UUID teacherId, @Param("date") LocalDate date, @Param("excludeId") UUID excludeId);

    /** Day values of the live requirements of the Schools in the range, by School and status (spec 014b A3). */
    @Query("""
            select r.schoolId, r.status, sum(r.dayValue) from SubstitutionRequirement r
            where r.schoolId in :schoolIds
              and r.status <> com.hls.substitution.internal.RequirementStatus.CANCELLED
              and r.dutyDate between :from and :to
            group by r.schoolId, r.status
            """)
    List<Object[]> daysBySchoolAndStatus(
            @Param("schoolIds") Collection<UUID> schoolIds, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
