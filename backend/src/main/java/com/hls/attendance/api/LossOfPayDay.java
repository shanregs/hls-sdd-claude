package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One unpaid day of a Teacher: the date, how much of the day (0.50 or 1.00) and the School it was marked at. */
public record LossOfPayDay(LocalDate date, BigDecimal dayValue, UUID schoolId) {}
