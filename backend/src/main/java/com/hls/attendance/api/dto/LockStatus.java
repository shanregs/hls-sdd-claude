package com.hls.attendance.api.dto;

/** A teacher-month's lock state (FR-011/FR-013). Absence of any lock row means {@code UNLOCKED}. */
public enum LockStatus {
    UNLOCKED,
    LOCKED,
    REOPENED
}
