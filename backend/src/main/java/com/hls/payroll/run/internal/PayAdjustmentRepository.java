package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PayAdjustmentRepository extends JpaRepository<PayAdjustment, UUID> {

    /** Waiting adjustments of the given people, oldest first: the ones a run takes. */
    @Query("select a from PayAdjustment a where a.status = com.hls.payroll.run.internal.PayAdjustment$Status.WAITING"
            + " and a.personKind = :kind and a.personId in :ids order by a.createdAt, a.id")
    List<PayAdjustment> waitingFor(@Param("kind") Kind kind, @Param("ids") Collection<UUID> ids);

    List<PayAdjustment> findByAttachedRunId(UUID runId);

    @Query("select a from PayAdjustment a where (:status is null or a.status = :status)"
            + " order by a.createdAt desc, a.id")
    Page<PayAdjustment> search(@Param("status") PayAdjustment.Status status, Pageable pageable);

    @Query("select a from PayAdjustment a where a.personKind = :kind and a.personId = :personId"
            + " and (:status is null or a.status = :status) order by a.createdAt desc, a.id")
    Page<PayAdjustment> searchPerson(
            @Param("kind") Kind kind,
            @Param("personId") UUID personId,
            @Param("status") PayAdjustment.Status status,
            Pageable pageable);
}
