package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface IssueRepository extends JpaRepository<PerformanceIssue, UUID>, JpaSpecificationExecutor<PerformanceIssue> {
    Optional<PerformanceIssue> findFirstByTeacherIdAndStatusIn(UUID teacherId, Collection<IssueStatus> statuses);

    List<PerformanceIssue> findByTeacherIdOrderByOpenedOnDescCreatedAtDesc(UUID teacherId);

    List<PerformanceIssue> findByStatusIn(Collection<IssueStatus> statuses);
}
