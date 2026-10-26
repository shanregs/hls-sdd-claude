package com.hls.schoolbilling.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MouApprovalRepository extends JpaRepository<MouApproval, UUID> {

    Optional<MouApproval> findByContractId(UUID contractId);

    /** The approval row locked for update; a decision, a submission and a term edit all take it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MouApproval a where a.contractId = :contractId")
    Optional<MouApproval> lockByContractId(@Param("contractId") UUID contractId);

    List<MouApproval> findByContractIdIn(Collection<UUID> contractIds);

    List<MouApproval> findByStatusOrderBySubmittedAtAsc(MouApprovalStatus status);
}
