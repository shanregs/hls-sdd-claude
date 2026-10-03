package com.hls.identity.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A settable {@link Clock} for integration tests that need to simulate time passing (a 14-day
 * renewal window, a 30-minute lockout) without a real wait.
 */
public class MutableTestClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    public MutableTestClock(Instant initial, ZoneId zone) {
        this.current = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public void advance(Duration duration) {
        current.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableTestClock(current.get(), zone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
