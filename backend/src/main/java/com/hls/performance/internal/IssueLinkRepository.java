package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.SourceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {
    List<IssueLink> findByIssueIdOrderByLinkedOn(UUID issueId);

    boolean existsByIssueIdAndRecordTypeAndRecordRootId(UUID issueId, SourceType recordType, UUID recordRootId);
}
