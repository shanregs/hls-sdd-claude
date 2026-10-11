package com.hls.payroll.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Save and query only: there is no update or delete, and the table refuses both (spec 013a FR-001). */
public interface DesignationSalaryRepository extends JpaRepository<DesignationSalary, UUID> {

    /** The row in effect on {@code date}: latest effective date, ties to the row recorded last. One index lookup. */
    Optional<DesignationSalary> findFirstByDesignationIdAndEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(
            UUID designationId, LocalDate date);

    List<DesignationSalary> findByDesignationIdOrderByEffectiveOnDescRecordedAtDesc(UUID designationId);

    /** Every row of the given designations on or before {@code date}; the one in effect is resolved in memory. */
    @Query("select s from DesignationSalary s where s.designationId in :designationIds and s.effectiveOn <= :date")
    List<DesignationSalary> findCandidates(
            @Param("designationIds") Collection<UUID> designationIds, @Param("date") LocalDate date);
}
