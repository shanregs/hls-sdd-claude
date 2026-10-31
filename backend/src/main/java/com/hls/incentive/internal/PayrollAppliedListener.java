package com.hls.incentive.internal;

import com.hls.payroll.api.PayrollAdjustmentApplied;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Marks an incentive "paid in run" when an approved pay run applies its adjustment (spec 030 US3, T020). Runs inside the pay
 * run approval transaction and is idempotent: a repeat of the event, or an event for another kind or source, changes nothing.
 */
@Component
class PayrollAppliedListener {

    private static final String PREFIX = "INCENTIVE:";

    private final IncentiveRepository incentives;
    private final IncentiveRecorder recorder;
    private final Clock clock;

    PayrollAppliedListener(IncentiveRepository incentives, IncentiveRecorder recorder, Clock clock) {
        this.incentives = incentives;
        this.recorder = recorder;
        this.clock = clock;
    }

    @EventListener
    void onApplied(PayrollAdjustmentApplied event) {
        if (!"INCENTIVE".equals(event.kind()) || event.sourceRef() == null || !event.sourceRef().startsWith(PREFIX)) {
            return;
        }
        Optional<UUID> id = parse(event.sourceRef().substring(PREFIX.length()));
        if (id.isEmpty()) {
            return;
        }
        incentives.lockById(id.get()).ifPresent(incentive -> {
            if (incentive.getStatus() == IncentiveStatus.PAID_IN_RUN) {
                return;
            }
            incentive.markPaid(event.runId(), event.month(), clock.instant());
            incentives.saveAndFlush(incentive);
            recorder.record(null, incentive.getId(), "APPLIED", "Paid in the pay run of " + event.month());
            recorder.field(null, incentive.getId(), "status", IncentiveStatus.APPROVED, IncentiveStatus.PAID_IN_RUN);
        });
    }

    private static Optional<UUID> parse(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
