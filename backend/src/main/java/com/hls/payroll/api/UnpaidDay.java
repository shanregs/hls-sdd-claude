package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One unpaid date; {@code dayValue} is 1 for a full day or the policy's half-day fraction for a half day. */
public record UnpaidDay(LocalDate date, BigDecimal dayValue) {}
