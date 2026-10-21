package com.hls.performance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface FeedbackRepository extends JpaRepository<PrincipalFeedback, UUID>, JpaSpecificationExecutor<PrincipalFeedback> {
    Optional<PrincipalFeedback> findByRootIdAndCurrentTrue(UUID rootId);

    List<PrincipalFeedback> findByRootIdOrderByVersionNoDesc(UUID rootId);

    List<PrincipalFeedback> findByTeacherIdAndCurrentTrueOrderByGivenOnDescCreatedAtDesc(UUID teacherId);
}
