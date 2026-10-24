package com.hls.reporting.internal;

/** An area, tile or Zone that is not in the caller's manifest or scope; answered as 404 so nothing leaks (spec 028 FR-020). */
public class DashboardNotFoundException extends RuntimeException {

    public DashboardNotFoundException(String message) {
        super(message);
    }
}
