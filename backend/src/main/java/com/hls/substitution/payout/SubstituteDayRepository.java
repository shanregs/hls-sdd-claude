package com.hls.substitution.payout;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubstituteDayRepository
        extends JpaRepository<SubstituteDay, UUID>, JpaSpecificationExecutor<SubstituteDay> {

    Optional<SubstituteDay> findByRequirementIdAndSubstituteTeacherId(UUID requirementId, UUID substituteTeacherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SubstituteDay d where d.id = :id")
    Optional<SubstituteDay> lockById(@Param("id") UUID id);

    /** A substitute's days in the date range, oldest first. */
    @Query("""
            select d from SubstituteDay d
            where d.substituteTeacherId = :teacherId and d.dayDate between :from and :to
            order by d.dayDate, d.id
            """)
    List<SubstituteDay> ofTeacher(
            @Param("teacherId") UUID teacherId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select d from SubstituteDay d
            where d.substituteTeacherId in :teacherIds and d.dayDate between :from and :to
            order by d.dayDate, d.id
            """)
    List<SubstituteDay> ofTeachers(
            @Param("teacherIds") Collection<UUID> teacherIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every day in the date range regardless of substitute (month views and the run's port). */
    @Query("select d from SubstituteDay d where d.dayDate between :from and :to order by d.dayDate, d.id")
    List<SubstituteDay> between(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The live days of the substitute in the range that nobody has marked. */
    @Query("""
            select d from SubstituteDay d
            where d.substituteTeacherId = :teacherId and d.dayDate between :from and :to
              and d.live = true and d.attendance = com.hls.substitution.payout.DayAttendance.NOT_MARKED
            order by d.dayDate
            """)
    List<SubstituteDay> unmarkedOf(
            @Param("teacherId") UUID teacherId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct d.substituteTeacherId from SubstituteDay d where d.dayDate between :from and :to")
    List<UUID> substitutesWithDays(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
