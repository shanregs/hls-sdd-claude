package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.PlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PlanRepository extends JpaRepository<ObservationPlan, UUID>, JpaSpecificationExecutor<ObservationPlan> {
    Optional<ObservationPlan> findFirstByTeacherIdAndStatus(UUID teacherId, PlanStatus status);

    List<ObservationPlan> findByStatus(PlanStatus status);
}
