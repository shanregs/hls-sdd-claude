package com.hls.activitycalendar.internal;

import java.time.LocalDate;
import java.util.List;

/** The response of {@code GET /api/v1/activity-calendar}. */
public record CalendarResponse(
        String month,
        LocalDate today,
        boolean partial,
        List<SourceReport> sources,
        CalendarSummary summary,
        List<CalendarItem> items) {}
