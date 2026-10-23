package com.hls.performance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ObservationRepository extends JpaRepository<Observation, UUID>, JpaSpecificationExecutor<Observation> {
    Optional<Observation> findByRootIdAndCurrentTrue(UUID rootId);

    List<Observation> findByRootIdOrderByVersionNoDesc(UUID rootId);

    List<Observation> findByTeacherIdAndCurrentTrueOrderByObservedOnDescCreatedAtDesc(UUID teacherId);
}
