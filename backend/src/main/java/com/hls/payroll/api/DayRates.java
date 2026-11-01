package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The rupees-per-full-day rate of a designation on a date (spec 017b, amendment to 013a). The rate on a date is the
 * latest row effective on or before it; the row recorded last wins a tie. Server-side only.
 */
public interface DayRates {

    sealed interface RateOn permits Found, None {}

    record Found(BigDecimal perDay, LocalDate effectiveOn) implements RateOn {}

    record None(String reason) implements RateOn {}

    RateOn rateOn(UUID designationId, LocalDate date);

    /** The rate of each given designation on the date, in a fixed number of queries; every id is in the result. */
    Map<UUID, RateOn> ratesOn(Collection<UUID> designationIds, LocalDate date);
}
