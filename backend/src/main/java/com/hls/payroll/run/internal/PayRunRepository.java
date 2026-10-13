package com.hls.payroll.run.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PayRunRepository extends JpaRepository<PayRun, UUID> {

    /** The run row under a write lock, for every state change (submit, return, approve, cancel, recompute). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PayRun r where r.id = :id")
    Optional<PayRun> lockById(@Param("id") UUID id);

    @Query("select r from PayRun r where (:status is null or r.status = :status) order by r.month desc, r.kind, r.sequence desc")
    Page<PayRun> search(@Param("status") PayRun.Status status, Pageable pageable);

    List<PayRun> findByMonthOrderByKindAscSequenceAsc(LocalDate month);

    @Query("select coalesce(max(r.sequence), 0) from PayRun r where r.month = :month and r.kind = :kind")
    int maxSequence(@Param("month") LocalDate month, @Param("kind") PayRun.Kind kind);

    @Query("select r from PayRun r where r.month = :month and r.kind = com.hls.payroll.run.internal.PayRun$Kind.PRIMARY and r.status = com.hls.payroll.run.internal.PayRun$Status.APPROVED")
    Optional<PayRun> approvedPrimary(@Param("month") LocalDate month);
}
