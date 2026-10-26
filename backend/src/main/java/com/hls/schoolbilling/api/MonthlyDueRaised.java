package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/** A School's monthly due was raised (spec 022). */
public record MonthlyDueRaised(UUID dueId, UUID schoolId, YearMonth month, BigDecimal total) {}
