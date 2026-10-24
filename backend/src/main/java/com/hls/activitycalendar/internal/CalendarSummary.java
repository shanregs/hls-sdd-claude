package com.hls.activitycalendar.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The counts above the calendar. Totals count each activity once (a multi-date drive counts once, with the status of
 * its last date); {@code planned = done + open + missed} and {@code cancelled} stays outside planned.
 */
public record CalendarSummary(
        int planned,
        int done,
        int doneLate,
        int open,
        int missed,
        int cancelled,
        int rescheduled,
        Map<String, Counts> byKind,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ManagerCounts> byManager) {

    /** Counts of one group of activities. */
    public record Counts(int planned, int done, int doneLate, int open, int missed, int cancelled, int rescheduled) {}

    /** Counts of one Manager; a visit shared by two Managers counts for each of them. */
    public record ManagerCounts(
            UUID userId, String name, int planned, int done, int doneLate, int open, int missed, int cancelled, int rescheduled) {}
}
