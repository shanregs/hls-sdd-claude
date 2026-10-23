package com.hls.support;

import com.hls.payroll.api.PayrollAdjustmentApplied;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Test-only listener that keeps the {@link PayrollAdjustmentApplied} events (spec 015). The tests call the server over
 * HTTP, so the events are published on server threads and cannot be seen with Spring's per-thread ApplicationEvents.
 */
@Component
public class AdjustmentAppliedRecorder {

    private final Queue<PayrollAdjustmentApplied> applied = new ConcurrentLinkedQueue<>();

    @EventListener
    void on(PayrollAdjustmentApplied event) {
        applied.add(event);
    }

    public List<PayrollAdjustmentApplied> forSource(String sourceRef) {
        return applied.stream().filter(e -> sourceRef.equals(e.sourceRef())).toList();
    }
}
