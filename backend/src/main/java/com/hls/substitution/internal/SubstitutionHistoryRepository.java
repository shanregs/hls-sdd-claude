package com.hls.substitution.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubstitutionHistoryRepository extends JpaRepository<SubstitutionHistoryEntry, UUID> {

    List<SubstitutionHistoryEntry> findByRequirementIdOrderByAtAscIdAsc(UUID requirementId);

    List<SubstitutionHistoryEntry> findByRequirementIdInOrderByAtAscIdAsc(java.util.Collection<UUID> requirementIds);
}
