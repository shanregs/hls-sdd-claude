package com.hls.activitycalendar.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * The single place where a calendar status is derived from the owning module's status (data-model.md, State rules):
 * cancelled gives CANCELLED; completed, held or verified gives DONE (late when completed after the date); otherwise a
 * date before the business today gives MISSED; anything else stays PLANNED. A multi-date drive is judged on its last date.
 */
public final class StatusRules {

    public static final String PLANNED = "PLANNED";
    public static final String DONE = "DONE";
    public static final String MISSED = "MISSED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> DONE_NATIVE = Set.of("COMPLETED", "HELD", "VERIFIED");

    /** The derived status of one item; {@code daysLate} is set for MISSED only. */
    public record Result(String status, boolean doneLate, Integer daysLate) {}

    private StatusRules() {}

    /**
     * @param nativeStatus the status name in the owning module
     * @param date the item's own date
     * @param judgedOn the date missed-ness is judged on: the last date of a drive, else the date itself
     * @param completedOn the business date it was completed or held, may be null
     * @param today the business date today
     */
    public static Result derive(String nativeStatus, LocalDate date, LocalDate judgedOn, LocalDate completedOn, LocalDate today) {
        if ("CANCELLED".equals(nativeStatus)) {
            return new Result(CANCELLED, false, null);
        }
        if (DONE_NATIVE.contains(nativeStatus)) {
            return new Result(DONE, completedOn != null && completedOn.isAfter(date), null);
        }
        LocalDate judged = judgedOn == null ? date : judgedOn;
        if (judged.isBefore(today)) {
            return new Result(MISSED, false, daysLate(judged, today));
        }
        return new Result(PLANNED, false, null);
    }

    /** Days between the date and today for an item that is still open after its date. */
    public static int daysLate(LocalDate date, LocalDate today) {
        return (int) ChronoUnit.DAYS.between(date, today);
    }

    /** True when the item was moved at least once (its first planned date differs from its current one). */
    public static boolean rescheduled(LocalDate originalDate, LocalDate date) {
        return originalDate != null && !originalDate.equals(date);
    }

    /** Days between the first planned date and the current one (always positive), null when never moved. */
    public static Integer daysMoved(LocalDate originalDate, LocalDate date) {
        return rescheduled(originalDate, date) ? (int) Math.abs(ChronoUnit.DAYS.between(originalDate, date)) : null;
    }
}
