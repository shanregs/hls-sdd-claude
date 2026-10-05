package com.hls.training.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InductionBatchRepository extends JpaRepository<InductionBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InductionBatch b where b.id = :id")
    Optional<InductionBatch> findForUpdate(@Param("id") UUID id);

    /** Batches that have not been cancelled and have not ended, earliest start first. */
    @Query("""
            select b from InductionBatch b
            where b.status <> 'CANCELLED' and b.endsOn >= :today
            order by b.startsOn, b.createdAt
            """)
    List<InductionBatch> openFrom(@Param("today") LocalDate today);

    List<InductionBatch> findAllByOrderByStartsOnDesc();
}
