package com.hls.identity.internal;

/** FR-013: every event this module's audit trail records. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCESS_DENIED,
    SESSION_REVOKED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED
}
