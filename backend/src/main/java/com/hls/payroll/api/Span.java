package com.hls.payroll.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One stretch of a month a person was payable in one place, clipped to the month. {@code schoolId} is null for a
 * Manager. {@code workingDays} counts the payable working dates inside {@code from..to} on that School's calendar.
 */
public record Span(UUID schoolId, LocalDate from, LocalDate to, int workingDays) {}
