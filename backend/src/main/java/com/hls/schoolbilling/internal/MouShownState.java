package com.hls.schoolbilling.internal;

/**
 * What a MoU is shown as (spec 031 research.md section 5). "Approved without document" and "approved before the approval
 * step" are views of the stored status plus the current document, never stored themselves.
 */
public enum MouShownState {
    NOT_APPLICABLE,
    CANCELLED,
    DRAFT,
    PENDING,
    REJECTED,
    APPROVED,
    APPROVED_WITHOUT_DOCUMENT,
    APPROVED_BEFORE_APPROVAL_STEP;

    /**
     * @param status the stored approval status; null when the contract has no approval row
     * @param hasCurrentDocument a version that is not removed exists
     */
    public static MouShownState derive(ContractState contract, MouApprovalStatus status, boolean hasCurrentDocument) {
        if (contract == ContractState.CANCELLED) {
            return CANCELLED;
        }
        if (contract == ContractState.RATE_PENDING) {
            return NOT_APPLICABLE;
        }
        if (status == null) {
            // an active MoU without an approval row cannot be told to be approved: the conservative reading
            return DRAFT;
        }
        return switch (status) {
            case DRAFT -> DRAFT;
            case PENDING -> PENDING;
            case REJECTED -> REJECTED;
            case APPROVED_LEGACY -> APPROVED_BEFORE_APPROVAL_STEP;
            case APPROVED -> hasCurrentDocument ? APPROVED : APPROVED_WITHOUT_DOCUMENT;
        };
    }

    /** True for the three approved views. */
    public boolean countsAsApproved() {
        return this == APPROVED || this == APPROVED_WITHOUT_DOCUMENT || this == APPROVED_BEFORE_APPROVAL_STEP;
    }
}
