package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProposalRevisionRepository extends JpaRepository<ProposalRevision, UUID> {

    List<ProposalRevision> findByProspectIdOrderByRevisionDesc(UUID prospectId);

    boolean existsByProspectId(UUID prospectId);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(r.revision), 0) from ProposalRevision r where r.prospectId = :prospectId")
    int latestNumber(@org.springframework.data.repository.query.Param("prospectId") UUID prospectId);
}
