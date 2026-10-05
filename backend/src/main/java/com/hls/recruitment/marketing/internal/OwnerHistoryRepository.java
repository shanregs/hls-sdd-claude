package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OwnerHistoryRepository extends JpaRepository<OwnerHistory, UUID> {

    List<OwnerHistory> findByProspectIdOrderByChangedAtAsc(UUID prospectId);
}
