package com.hls.expenses.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExpenseBillRepository extends JpaRepository<ExpenseBill, UUID> {

    List<ExpenseBill> findByClaimIdOrderByAddedAt(UUID claimId);

    List<ExpenseBill> findByClaimIdInOrderByAddedAt(Collection<UUID> claimIds);

    long countByClaimIdAndRemovedAtIsNull(UUID claimId);
}
