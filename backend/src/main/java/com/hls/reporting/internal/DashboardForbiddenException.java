package com.hls.reporting.internal;

/** The caller may not use this part of the Dashboard (for example the Manager comparison as a Manager); answered as 403. */
public class DashboardForbiddenException extends RuntimeException {

    public DashboardForbiddenException(String message) {
        super(message);
    }
}
