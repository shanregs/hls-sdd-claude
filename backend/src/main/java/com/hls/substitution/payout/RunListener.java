package com.hls.substitution.payout;

import com.hls.payroll.api.PayrollRunApproved;
import com.hls.payroll.api.PayrollRunReturned;
import com.hls.payroll.api.PayrollRunSubmitted;
import com.hls.substitution.payout.DayEvent.Cause;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the status of the substitute days in step with the pay runs (spec 017b FR-017): a run that is submitted,
 * approved or returned changes what it holds for the month. Runs inside the publisher's transaction, so the days and
 * the approval commit together.
 */
@Component
class RunListener {

    private final DayService service;

    RunListener(DayService service) {
        this.service = service;
    }

    @EventListener
    void onSubmitted(PayrollRunSubmitted event) {
        service.refreshMonth(event.month(), Cause.JOB);
    }

    @EventListener
    void onApproved(PayrollRunApproved event) {
        service.refreshMonth(event.month(), Cause.RUN_APPROVED);
    }

    @EventListener
    void onReturned(PayrollRunReturned event) {
        service.refreshMonth(event.month(), Cause.JOB);
    }
}
