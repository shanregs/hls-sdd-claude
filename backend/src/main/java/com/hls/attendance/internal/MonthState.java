package com.hls.attendance.internal;

/** A Teacher-month row exists only once locked; OPEN means reopened for correction. */
public enum MonthState {
    LOCKED,
    OPEN
}
