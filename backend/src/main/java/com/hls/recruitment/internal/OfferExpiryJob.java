package com.hls.recruitment.internal;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires issued offers past their response deadline, daily at 02:45 unless the property below is false. */
@Component
class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);
    /** The audit trail names the system, not a person, for changes the job makes. */
    static final UUID SYSTEM = new UUID(0L, 0L);

    private final OfferService offers;
    private final boolean enabled;

    OfferExpiryJob(OfferService offers, @Value("${hls.recruitment.offer-expiry.enabled:true}") boolean enabled) {
        this.offers = offers;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 45 2 * * *")
    void run() {
        if (!enabled) {
            return;
        }
        int expired = offers.expireOverdue(SYSTEM);
        log.info("Expired {} job offers past their deadline", expired);
    }

    /** For tests and operators: runs the same work regardless of the schedule switch. */
    int runNow() {
        return offers.expireOverdue(SYSTEM);
    }
}
