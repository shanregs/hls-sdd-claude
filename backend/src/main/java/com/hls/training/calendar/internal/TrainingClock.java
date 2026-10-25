package com.hls.training.calendar.internal;

import com.hls.designation.api.BusinessDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/** Business-zone "today" and "now" for the training calendar: session dates and times are plain local values (no conversion). */
@Component
public class TrainingClock {

    private final Clock clock;
    private final BusinessDate businessDate;

    TrainingClock(Clock clock, BusinessDate businessDate) {
        this.clock = clock;
        this.businessDate = businessDate;
    }

    public Instant instant() {
        return clock.instant();
    }

    public LocalDate today() {
        return businessDate.today();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(businessDate.zone()));
    }

    /** True once the session's start has passed in the business zone. */
    public boolean started(LocalDate date, LocalTime startsAt) {
        return !now().isBefore(LocalDateTime.of(date, startsAt));
    }
}
