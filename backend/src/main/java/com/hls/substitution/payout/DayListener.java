package com.hls.substitution.payout;

import com.hls.substitution.api.SubstitutionAssigned;
import com.hls.substitution.api.SubstitutionEnded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the substitute day records in step with the allocations of spec 017a (FR-010). Runs inside the publisher's
 * transaction, so an allocation and its day record commit or roll back together.
 */
@Component
class DayListener {

    private final DayService service;

    DayListener(DayService service) {
        this.service = service;
    }

    @EventListener
    void onAssigned(SubstitutionAssigned event) {
        service.onAssigned(event);
    }

    @EventListener
    void onEnded(SubstitutionEnded event) {
        service.onEnded(event);
    }
}
