package com.hls.payroll.run.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    Optional<Payslip> findByLineId(UUID lineId);

    List<Payslip> findByRunId(UUID runId);
}
