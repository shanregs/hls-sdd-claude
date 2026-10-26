package com.hls.schoolbilling.internal;

/** The stored status of a MoU approval (spec 031 data-model.md). */
public enum MouApprovalStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    /** A MoU recorded before the approval step existed; never changes and is counted as approved. */
    APPROVED_LEGACY
}
