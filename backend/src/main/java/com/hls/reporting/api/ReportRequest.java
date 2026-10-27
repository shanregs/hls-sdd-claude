package com.hls.reporting.api;

import java.time.YearMonth;
import java.util.UUID;

/** What the caller asked for: the month and the filters, already parsed. A filter is null when not given. */
public record ReportRequest(
        YearMonth month, UUID zoneId, UUID schoolId, UUID teacherId, UUID managerId, String season) {

    /** A request with no season (spec 014c A0 keeps the original five-argument form). */
    public ReportRequest(YearMonth month, UUID zoneId, UUID schoolId, UUID teacherId, UUID managerId) {
        this(month, zoneId, schoolId, teacherId, managerId, null);
    }

    /** The same request for another month. */
    public ReportRequest forMonth(YearMonth other) {
        return new ReportRequest(other, zoneId, schoolId, teacherId, managerId, season);
    }
}
