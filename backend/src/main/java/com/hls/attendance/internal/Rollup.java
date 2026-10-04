package com.hls.attendance.internal;

import java.math.BigDecimal;

/**
 * The monthly figures for one Teacher (spec 008 FR-010). Day counts are decimals because half days
 * count as 0.5.
 */
public record Rollup(
        BigDecimal workingDays,
        BigDecimal daysWorked,
        BigDecimal daysLeave,
        BigDecimal trainingAvailable,
        BigDecimal trainingAttended,
        int unmarked,
        BigDecimal weightedTotal) {}
