package com.hls.designation.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Today's date in the business time zone (Asia/Kolkata by default), so month boundaries are right early in the day. */
@Component
public class BusinessDate {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessDate(Clock clock, @Value("${hls.business-timezone:Asia/Kolkata}") String zoneId) {
        this.clock = clock;
        this.zone = ZoneId.of(zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}
