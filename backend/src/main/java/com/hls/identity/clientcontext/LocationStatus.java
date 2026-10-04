package com.hls.identity.clientcontext;

/** Outcome of the location capture sent with a request (spec 018 data-model.md). */
public enum LocationStatus {
    AVAILABLE,
    PERMISSION_DENIED,
    SERVICES_OFF,
    NO_FIX,
    INVALID,
    OTHER,
    NOT_APPLICABLE
}
