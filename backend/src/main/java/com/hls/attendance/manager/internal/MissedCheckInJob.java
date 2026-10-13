package com.hls.attendance.manager.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Raises the missed check-in alert (spec 032 US4). Runs every two minutes, so an alert reaches people well inside the
 * five minutes of SC-005, unless {@code hls.attendance.manager.missed-check-in.enabled} is false.
 */
@Component
class MissedCheckInJob {

    private static final Logger log = LoggerFactory.getLogger(MissedCheckInJob.class);

    /** The schedule period; a test holds it to two minutes or less. */
    static final long PERIOD_MS = 120_000L;

    private final MissedCheckInService service;
    private final boolean enabled;

    MissedCheckInJob(
            MissedCheckInService service,
            @Value("${hls.attendance.manager.missed-check-in.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedRate = PERIOD_MS, initialDelay = 30_000L)
    void run() {
        if (!enabled) {
            return;
        }
        try {
            runNow();
        } catch (RuntimeException e) {
            log.warn("The missed check-in alert could not run: {}", e.toString());
        }
    }

    /** For tests and operators: runs the same work regardless of the schedule switch. */
    int runNow() {
        int raised = service.alertDue(service.today(), service.now());
        if (raised > 0) {
            log.info("Raised {} missed check-in alert(s)", raised);
        }
        return raised;
    }
}
