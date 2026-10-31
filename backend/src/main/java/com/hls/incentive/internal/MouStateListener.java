package com.hls.incentive.internal;

import com.hls.schoolbilling.api.MouDecided;
import com.hls.schoolbilling.api.MouSubmitted;
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
    private final HoldService holds;

    MouStateListener(EarningService earning, HoldService holds) {
        this.earning = earning;
        this.holds = holds;
    }

    @ApplicationModuleListener
    void onDecided(MouDecided event) {
        try {
            if ("APPROVED".equals(event.outcome())) {
                earning.earn(event.schoolId());
            }
            holds.reevaluate(event.schoolId());
        } catch (RuntimeException e) {
            log.warn("Could not update the incentive of School {} after a MoU decision; the daily job will retry", event.schoolId(), e);
        }
    }

    /** A submission, resubmission or reset: an approved MoU may have stopped counting. */
    @ApplicationModuleListener
    void onSubmitted(MouSubmitted event) {
        try {
            holds.reevaluate(event.schoolId());
        } catch (RuntimeException e) {
            log.warn("Could not update the incentive of School {} after a MoU submission; the daily job will retry", event.schoolId(), e);
        }
    }
}
