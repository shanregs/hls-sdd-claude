package com.hls.leave.internal;

/** The life of a leave request (spec 009 data-model.md state machine). REJECTED and CANCELLED are final. */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
