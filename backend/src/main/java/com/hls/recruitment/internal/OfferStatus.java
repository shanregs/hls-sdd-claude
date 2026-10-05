package com.hls.recruitment.internal;

public enum OfferStatus {
    DRAFT,
    ISSUED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    SUPERSEDED;

    /** An offer that still blocks a second one for the same person. */
    public boolean isOpen() {
        return this == DRAFT || this == ISSUED;
    }
}
