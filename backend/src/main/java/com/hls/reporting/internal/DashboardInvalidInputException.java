package com.hls.reporting.internal;

/** A request parameter that cannot be read, such as a bad {@code period}; answered as 400. */
public class DashboardInvalidInputException extends RuntimeException {

    public DashboardInvalidInputException(String message) {
        super(message);
    }
}
