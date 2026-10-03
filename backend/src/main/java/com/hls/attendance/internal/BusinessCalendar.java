package com.hls.attendance.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only source of "today" for attendance (research.md section 9): the application {@link Clock} is
 * UTC, but business dates are Indian dates, which differ from UTC between 00:00 and 05:30 IST.
 */
@Component
public class BusinessCalendar {

    /** A Teacher may mark their own attendance for today and this many previous days. */
    public static final int SELF_MARK_WINDOW_DAYS = 3;

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, @Value("${hls.business-timezone:Asia/Kolkata}") String zoneId) {
        this.clock = clock;
        this.zone = ZoneId.of(zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public LocalDate windowStart() {
        return today().minusDays(SELF_MARK_WINDOW_DAYS);
    }

    /** A month has ended once its last day is before today. */
    public boolean monthHasEnded(YearMonth month) {
        return month.atEndOfMonth().isBefore(today());
    }
}
