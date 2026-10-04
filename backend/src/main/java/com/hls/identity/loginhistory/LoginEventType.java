package com.hls.identity.loginhistory;

/** The full set of authentication outcomes FR-019 requires a login-history event for. */
public enum LoginEventType {
    SIGN_IN_SUCCESS,
    SIGN_IN_FAILURE,
    LOCKOUT,
    LOGOUT,
    SESSION_ENDED_BY_USER,
    SESSION_ENDED_BY_ADMIN,
    SESSION_REVOKED_REUSE,
    OTP_REQUESTED,
    PASSWORD_RESET
}
