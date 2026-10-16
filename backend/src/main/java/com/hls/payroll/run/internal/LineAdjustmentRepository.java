package com.hls.payroll.run.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LineAdjustmentRepository extends JpaRepository<LineAdjustment, UUID> {

    List<LineAdjustment> findByLineId(UUID lineId);

    List<LineAdjustment> findByLineIdIn(Collection<UUID> lineIds);

    List<LineAdjustment> findByAdjustmentId(UUID adjustmentId);

    void deleteByLineIdIn(Collection<UUID> lineIds);
}
