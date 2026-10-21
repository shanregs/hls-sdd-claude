package com.hls.performance.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReferralRepository extends JpaRepository<Referral, UUID> {
    Optional<Referral> findByIssueId(UUID issueId);

    List<Referral> findByIssueIdIn(Collection<UUID> issueIds);
}
