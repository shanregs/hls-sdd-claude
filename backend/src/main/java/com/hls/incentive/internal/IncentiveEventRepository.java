package com.hls.incentive.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncentiveEventRepository extends JpaRepository<IncentiveEvent, UUID> {

    List<IncentiveEvent> findByIncentiveIdOrderBySeqAsc(UUID incentiveId);
}
