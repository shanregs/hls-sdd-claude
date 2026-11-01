package com.hls.substitution.payout;

/** Where a substitute day stands, from allocation to payment (spec 017b FR-017, data-model.md). */
public enum DayStatus {
    ALLOCATED("Allocated, attendance not marked"),
    DID_NOT_ATTEND("Did not attend"),
    ATTENDED("Attended, month not locked yet"),
    CANNOT_PAY("Cannot be paid yet"),
    LOCKED_WAITING_RUN("Locked, waiting for the payroll run"),
    BACKPAY_WAITING("Waiting for a supplementary run"),
    IN_DRAFT_RUN("In a draft payroll run"),
    IN_RUN_AWAITING_APPROVAL("In a payroll run awaiting approval"),
    PAID("Paid in an approved run"),
    CANCELLED("Allocation cancelled"),
    CANCELLED_BEFORE_PAYMENT("Cancelled before payment"),
    CHANGED_RUN_AWAITING("Changed while the run awaits approval"),
    CORRECTION_NEEDED("Correction needed"),
    CORRECTION_RAISED("Correction raised"),
    CORRECTED("Corrected");

    private final String text;

    DayStatus(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
