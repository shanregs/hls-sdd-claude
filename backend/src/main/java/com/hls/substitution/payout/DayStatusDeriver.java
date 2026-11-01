package com.hls.substitution.payout;

/**
 * The one place the status of a substitute day is worked out (spec 017b data-model.md "Status derivation"). Pure: it
 * takes the facts and returns the status, so every row of the table is testable without a database.
 */
public final class DayStatusDeriver {

    /** What a payroll run holds for the day. */
    public enum RunHolding {
        NONE,
        DRAFT,
        AWAITING_APPROVAL,
        APPROVED
    }

    /** Whether an Admin Correction was raised for the day and whether payroll applied it. */
    public enum CorrectionState {
        NONE,
        RAISED,
        APPLIED
    }

    /**
     * @param live the allocation still stands
     * @param attendance what the Zone Manager recorded
     * @param monthLocked the substitute-month is locked
     * @param priceable a rate is known for the designation on that date and the substitute is an Active Substitute
     * @param run what the run holds for this day
     * @param monthRunApproved the month's primary run is approved (so a late day needs a supplementary run)
     * @param correction the Correction state
     */
    public record Facts(
            boolean live,
            DayAttendance attendance,
            boolean monthLocked,
            boolean priceable,
            RunHolding run,
            boolean monthRunApproved,
            CorrectionState correction) {}

    private DayStatusDeriver() {}

    public static DayStatus derive(Facts f) {
        if (f.correction() == CorrectionState.APPLIED) {
            return DayStatus.CORRECTED;
        }
        if (f.correction() == CorrectionState.RAISED) {
            return DayStatus.CORRECTION_RAISED;
        }
        if (!f.live()) {
            return switch (f.run()) {
                case NONE -> DayStatus.CANCELLED;
                case DRAFT -> DayStatus.CANCELLED_BEFORE_PAYMENT;
                case AWAITING_APPROVAL -> DayStatus.CHANGED_RUN_AWAITING;
                case APPROVED -> DayStatus.CORRECTION_NEEDED;
            };
        }
        return switch (f.attendance()) {
            case NOT_MARKED -> f.run() == RunHolding.APPROVED ? DayStatus.CORRECTION_NEEDED : DayStatus.ALLOCATED;
            case ABSENT -> switch (f.run()) {
                case APPROVED -> DayStatus.CORRECTION_NEEDED;
                case AWAITING_APPROVAL -> DayStatus.CHANGED_RUN_AWAITING;
                default -> DayStatus.DID_NOT_ATTEND;
            };
            case PRESENT -> present(f);
        };
    }

    private static DayStatus present(Facts f) {
        if (!f.monthLocked()) {
            return DayStatus.ATTENDED;
        }
        return switch (f.run()) {
            case DRAFT -> DayStatus.IN_DRAFT_RUN;
            case AWAITING_APPROVAL -> DayStatus.IN_RUN_AWAITING_APPROVAL;
            case APPROVED -> DayStatus.PAID;
            case NONE -> {
                if (!f.priceable()) {
                    yield DayStatus.CANNOT_PAY;
                }
                yield f.monthRunApproved() ? DayStatus.BACKPAY_WAITING : DayStatus.LOCKED_WAITING_RUN;
            }
        };
    }
}
