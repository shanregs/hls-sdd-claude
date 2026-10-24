package com.hls.expenses.internal;

/** Where a claim is in its life (spec 015 FR-004). */
public enum ClaimStatus {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    PAID("Paid"),
    PAID_BY_COMPANY("Paid by company");

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Draft and Rejected claims may be edited by their claimant. */
    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Counted as actual spend in the budget (Approved, Paid and Paid by company). */
    public boolean counted() {
        return this == APPROVED || this == PAID || this == PAID_BY_COMPANY;
    }

    public boolean settled() {
        return this == PAID || this == PAID_BY_COMPANY;
    }
}
