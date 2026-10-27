package com.hls.incentive.internal;

/** Where an incentive stands (spec 030 data-model.md). PAID_IN_RUN is final. */
public enum IncentiveStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    HELD,
    PAID_IN_RUN
}
