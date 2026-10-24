package com.hls.expenses.api;

import java.util.UUID;

/** A claim was approved, rejected or its approval reversed: the claimant hears of it. {@code outcome} is APPROVED, REJECTED or REVERSED. */
public record ExpenseDecided(UUID claimId, UUID claimantId, String outcome, String reason) {}
