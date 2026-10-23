package com.hls.performance.api;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only counts for the management dashboard (spec 028). A record belongs to the Manager (user id) who was the School's
 * Manager when it was made, and to the Zone of its School; planned and overdue observations and open issues are counted as
 * they stand today, the rest within the period.
 */
public interface PerformanceSummary {

    record Counts(
            long observationsDone,
            long observationsPlanned,
            long observationsOverdue,
            long openIssues,
            long referrals,
            long resolvedIssues) {}

    /** Keyed by the Manager's user id. */
    Map<UUID, Counts> byManager(LocalDate from, LocalDate to);

    /** Keyed by Zone id. */
    Map<UUID, Counts> byZone(LocalDate from, LocalDate to);
}
