package com.hls.payroll.api;

import java.time.YearMonth;
import java.util.UUID;

/** A pay run was returned to draft with a reason (spec 013b). Carries no amounts; becomes a notification for the submitter. */
public record PayrollRunReturned(UUID runId, YearMonth month, UUID returnedBy, UUID submittedBy, String reason) {}
