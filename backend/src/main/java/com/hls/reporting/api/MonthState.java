package com.hls.reporting.api;

/** The state of the month a report covers: attendance is locked or open; a payroll run is draft, approved or paid. */
public enum MonthState {
    LOCKED,
    OPEN,
    DRAFT,
    APPROVED,
    PAID,
    /** The month has no state (for example no run yet). */
    NONE
}
