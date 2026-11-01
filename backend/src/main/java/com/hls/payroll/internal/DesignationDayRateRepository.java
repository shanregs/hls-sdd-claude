package com.hls.payroll.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Save and query only: there is no update or delete, and the table refuses both (spec 017b FR-005). */
public interface DesignationDayRateRepository extends JpaRepository<DesignationDayRate, UUID> {

    List<DesignationDayRate> findByDesignationIdOrderByEffectiveOnDescRecordedAtDesc(UUID designationId);

    /** Every row of the given designations on or before {@code date}; the one in effect is resolved in memory. */
    @Query("select r from DesignationDayRate r where r.designationId in :designationIds and r.effectiveOn <= :date")
    List<DesignationDayRate> findCandidates(
            @Param("designationIds") Collection<UUID> designationIds, @Param("date") LocalDate date);
}
