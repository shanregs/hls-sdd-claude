package com.hls.expenses.internal;

import java.util.List;
import java.util.UUID;

/** Another claim has the same claimant, date, amount and category; the caller must confirm to continue (FR-008). */
public class DuplicateClaimException extends RuntimeException {

    private final transient List<UUID> matches;

    public DuplicateClaimException(List<UUID> matches) {
        super("You already have a claim with the same date, amount and category. Confirm to submit this one as well.");
        this.matches = matches;
    }

    public List<UUID> matches() {
        return matches;
    }
}
