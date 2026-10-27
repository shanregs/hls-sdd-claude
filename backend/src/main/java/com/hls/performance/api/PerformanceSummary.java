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

    /**
     * Keyed by Teacher id, with the same counts and rules as {@link #byManager} (spec 014c A4): a record counts for the
     * Teacher it is about, so the Teachers of a Manager add up to that Manager's row. Teachers with nothing are absent.
     */
    Map<UUID, Counts> byTeacher(LocalDate from, LocalDate to);

    /** Keyed by Zone id. */
    Map<UUID, Counts> byZone(LocalDate from, LocalDate to);
}
