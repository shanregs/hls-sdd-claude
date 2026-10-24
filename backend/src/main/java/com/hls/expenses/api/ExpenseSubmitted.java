package com.hls.expenses.api;

import java.util.UUID;

/** A claim was submitted (or resubmitted after a rejection or withdrawal): every Admin and Director hears of it. */
public record ExpenseSubmitted(UUID claimId, UUID claimantId, String claimantName, boolean resubmitted) {}
