package com.hls.recruitment.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CandidateOutcomeHistoryRepository extends JpaRepository<CandidateOutcomeHistory, UUID> {

    List<CandidateOutcomeHistory> findByCandidateIdOrderByChangedAtAsc(UUID candidateId);
}
