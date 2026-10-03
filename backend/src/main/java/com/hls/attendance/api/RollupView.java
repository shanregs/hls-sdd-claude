package com.hls.attendance.api;

import java.math.BigDecimal;

/**
 * A Teacher's monthly attendance figures (spec 008 FR-010). {@code frozen} is true when the month is
 * locked and the figures are the ones captured at lock; {@code locked} says the month cannot be edited.
 */
public record RollupView(
        BigDecimal workingDays,
        BigDecimal daysWorked,
        BigDecimal daysLeave,
        BigDecimal trainingAvailable,
        BigDecimal trainingAttended,
        int unmarked,
        BigDecimal weightedTotal,
        boolean locked,
        boolean frozen) {}
