package com.hls.incentive.internal;

import com.hls.schoolbilling.api.MouDecided;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the MoU events of spec 031 after the MoU transaction has committed, so a failure here can never block the
 * approval of a MoU (research D-1). A missed event is repaired by the daily reconcile job.
 */
@Component
class MouStateListener {

    private static final Logger log = LoggerFactory.getLogger(MouStateListener.class);

    private final EarningService earning;

    MouStateListener(EarningService earning) {
        this.earning = earning;
    }

    @ApplicationModuleListener
    void onDecided(MouDecided event) {
        if (!"APPROVED".equals(event.outcome())) {
            return;
        }
        try {
            earning.earn(event.schoolId());
        } catch (RuntimeException e) {
            log.warn("Could not look for an incentive for School {}; the daily job will retry", event.schoolId(), e);
        }
    }
}
