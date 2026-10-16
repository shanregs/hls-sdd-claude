package com.hls.payroll.api;

import java.time.YearMonth;
import java.util.UUID;

/** A pay run was submitted for approval (spec 013b). Carries no amounts and no names; becomes a notification for the approvers. */
public record PayrollRunSubmitted(UUID runId, YearMonth month, UUID submittedBy) {}
