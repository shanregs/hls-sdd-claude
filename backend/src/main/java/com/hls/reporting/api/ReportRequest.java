package com.hls.reporting.api;

import java.time.YearMonth;
import java.util.UUID;

/** What the caller asked for: the month and the filters, already parsed. A filter is null when not given. */
public record ReportRequest(YearMonth month, UUID zoneId, UUID schoolId, UUID teacherId, UUID managerId) {

    /** The same request for another month. */
    public ReportRequest forMonth(YearMonth other) {
        return new ReportRequest(other, zoneId, schoolId, teacherId, managerId);
    }
}
