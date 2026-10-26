package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A monthly due went overdue (spec 022 FR-016). {@code noManager} is true when the School has no Zone Manager who can hold a
 * task, so the Director and Admin are told instead.
 */
public record MonthlyDueOverdue(
        UUID dueId, UUID schoolId, String schoolName, YearMonth month, BigDecimal balance, long daysOverdue, boolean noManager) {}
