package com.hls.reporting.internal.report;

import java.time.YearMonth;
import java.util.Optional;

/** The month a report opens on: the last final (locked) month, else the current one (spec 014a FR-005). */
public final class DefaultMonth {

    private DefaultMonth() {}

    public static YearMonth choose(Optional<YearMonth> lastFinal, YearMonth current) {
        return lastFinal.filter(m -> !m.isAfter(current)).orElse(current);
    }
}
