package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A School contract (the MoU) as other modules see it. {@code state} is RATE_PENDING (shown as "MoU pending",
 * no positions), ACTIVE or CANCELLED. {@code rate} is set only for a same-salary-for-all contract.
 */
public record ContractView(
        UUID id,
        UUID schoolId,
        String state,
        String salaryMode,
        Integer teacherCount,
        BigDecimal rate,
        LocalDate signedOn,
        LocalDate startsOn,
        LocalDate endsOn,
        List<PositionView> positions) {}
