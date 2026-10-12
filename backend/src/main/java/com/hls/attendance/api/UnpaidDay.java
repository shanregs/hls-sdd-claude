package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date, or part of a date, a Manager was unpaid on: {@code fraction} is 1 or 0.5. */
public record UnpaidDay(LocalDate date, BigDecimal fraction) {}
