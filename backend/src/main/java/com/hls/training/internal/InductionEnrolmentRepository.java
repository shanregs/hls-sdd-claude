package com.hls.training.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InductionEnrolmentRepository extends JpaRepository<InductionEnrolment, UUID> {

    List<InductionEnrolment> findByBatchIdOrderByEnrolledAt(UUID batchId);

    List<InductionEnrolment> findByBatchIdIn(Collection<UUID> batchIds);

    List<InductionEnrolment> findByTeacherId(UUID teacherId);

    long countByBatchId(UUID batchId);

    /** Enrolments that still hold the Teacher on those dates (not signed off as not completed). */
    @Query("""
            select e from InductionEnrolment e
            where e.teacherId = :teacherId
              and (e.result is null or e.result = 'COMPLETED')
              and e.startsOn <= :endsOn and e.endsOn >= :startsOn
            """)
    List<InductionEnrolment> overlapping(
            @Param("teacherId") UUID teacherId, @Param("startsOn") LocalDate startsOn, @Param("endsOn") LocalDate endsOn);

    @Query("select e.teacherId from InductionEnrolment e where e.result = 'COMPLETED'")
    List<UUID> completedTeacherIds();
}
