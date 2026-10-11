package com.hls.payroll.internal;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which row of a history is in effect on a date (spec 013a FR-002, SC-001): the latest {@code effectiveOn} on or
 * before the date, and the row recorded last when two share a date. Pure: no clock, no repository, no state. The
 * same rule serves salaries and pay policy versions.
 */
public final class SalaryResolver {

    private static final Comparator<Dated> OLDEST_FIRST =
            Comparator.comparing(Dated::effectiveOn).thenComparing(Dated::recordedAt);

    private SalaryResolver() {}

    public static <T extends Dated> Optional<T> inEffect(List<T> rows, LocalDate date) {
        T best = null;
        for (T row : rows) {
            if (row.effectiveOn().isAfter(date)) {
                continue;
            }
            if (best == null || OLDEST_FIRST.compare(row, best) > 0) {
                best = row;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The whole history, newest first: latest effective date, then the one recorded last. */
    public static <T extends Dated> List<T> newestFirst(List<T> rows) {
        return rows.stream().sorted(OLDEST_FIRST.reversed()).toList();
    }
}
