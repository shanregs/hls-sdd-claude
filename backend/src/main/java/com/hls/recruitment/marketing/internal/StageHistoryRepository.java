package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StageHistoryRepository extends JpaRepository<StageHistory, UUID> {

    List<StageHistory> findByProspectIdOrderByChangedAtAsc(UUID prospectId);
}
