package com.hls.performance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, UUID> {
    List<CorrectiveAction> findByIssueIdOrderByRoundNo(UUID issueId);

    Optional<CorrectiveAction> findFirstByIssueIdOrderByRoundNoDesc(UUID issueId);
}
