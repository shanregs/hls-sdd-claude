package com.hls.payroll.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An adjustment that came from another module was applied by an approved pay run. Published inside the approval
 * transaction, once per applied adjustment that has a {@code sourceRef}. {@code kind} is the adjustment kind name
 * (for example {@code REIMBURSEMENT}); {@code month} is the first day of the run's month.
 */
public record PayrollAdjustmentApplied(String sourceRef, String kind, UUID runId, LocalDate month) {}
