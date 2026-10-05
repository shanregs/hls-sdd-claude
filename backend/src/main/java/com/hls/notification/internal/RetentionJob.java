package com.hls.notification.internal;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes notifications created more than 90 days ago (spec 010 FR-010). Retention counts from
 * {@code created_at}, so a notification that was merged and kept growing still expires with its first
 * event. Logs only how many rows went, never their content.
 */
@Component
public class RetentionJob {

    public static final Duration RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final NotificationService notifications;
    private final Clock clock;
    private final boolean enabled;

    public RetentionJob(
            NotificationService notifications,
            Clock clock,
            @Value("${hls.notification.retention.enabled:true}") boolean enabled) {
        this.notifications = notifications;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Runs daily at 02:30 server time unless {@code hls.notification.retention.enabled} is false. */
    @Scheduled(cron = "0 30 2 * * *")
    void scheduled() {
        if (enabled) {
            purge();
        }
    }

    /** Deletes what is older than the retention period and returns how many rows were removed. */
    public int purge() {
        int removed = notifications.purgeCreatedBefore(clock.instant().minus(RETENTION));
        log.info("Notification retention removed {} notification(s) older than {} days.", removed, RETENTION.toDays());
        return removed;
    }
}
