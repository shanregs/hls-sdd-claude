package com.hls.recruitment.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssessmentScoreRepository extends JpaRepository<AssessmentScore, UUID> {

    List<AssessmentScore> findByCandidateIdOrderByAssessmentNoAscCriterionAsc(UUID candidateId);

    List<AssessmentScore> findByCandidateIdIn(Collection<UUID> candidateIds);

    @Query("select coalesce(max(s.assessmentNo), 0) from AssessmentScore s where s.candidateId = :candidateId")
    int latestNumber(@Param("candidateId") UUID candidateId);
}
