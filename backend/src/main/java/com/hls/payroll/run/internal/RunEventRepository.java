package com.hls.payroll.run.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RunEventRepository extends JpaRepository<RunEvent, UUID> {

    List<RunEvent> findByRunIdOrderByAtAsc(UUID runId);
}
