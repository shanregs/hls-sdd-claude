package com.hls.payroll.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * The working dates of a month a person is payable for, oldest first. {@code workingDaysInMonth} is the month's
 * working days on the person's calendar: the default calendar for a Manager, and for a Teacher the calendar of the
 * School they were placed in last during the month (the default calendar when they were in none). Each date appears
 * once even when the person changed School in the month.
 */
public record PayableDays(YearMonth month, List<LocalDate> dates, int workingDaysInMonth, List<Span> spans) {}
