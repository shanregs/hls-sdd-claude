package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProposalPositionRepository extends JpaRepository<ProposalPosition, UUID> {

    List<ProposalPosition> findByRevisionIdInOrderByRevisionIdAscNumberAsc(java.util.Collection<UUID> revisionIds);
}
