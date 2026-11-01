package com.hls.payroll.api;

import java.time.YearMonth;
import java.util.UUID;

/**
 * A pay run's contents changed without a lifecycle event of its own (spec 017b): its computation finished, or the draft was
 * cancelled. {@code what} is {@code COMPUTED} or {@code CANCELLED}. Carries no amounts and no names. Published inside the
 * transaction that made the change, so a listener sees it together with the change.
 */
public record PayrollRunChanged(UUID runId, YearMonth month, String what) {}
