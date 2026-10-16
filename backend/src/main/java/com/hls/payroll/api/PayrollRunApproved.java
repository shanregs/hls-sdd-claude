package com.hls.payroll.api;

import java.time.YearMonth;
import java.util.UUID;

/** A pay run was approved and is now final (spec 013b). Carries no amounts and no names; becomes a notification for the submitter. */
public record PayrollRunApproved(UUID runId, YearMonth month, UUID approvedBy, UUID submittedBy) {}
