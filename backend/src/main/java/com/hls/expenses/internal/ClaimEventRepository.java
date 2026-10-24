package com.hls.expenses.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClaimEventRepository extends JpaRepository<ClaimEvent, UUID> {

    List<ClaimEvent> findByClaimIdOrderByAtAscIdAsc(UUID claimId);
}
