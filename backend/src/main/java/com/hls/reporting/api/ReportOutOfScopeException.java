package com.hls.reporting.api;

/**
 * Thrown by a report source (or the framework) when a filter names a Zone, School, Teacher or Manager outside the caller's
 * scope. It becomes a 403 and the request returns no figures (spec 014a FR-006).
 */
public class ReportOutOfScopeException extends RuntimeException {

    public ReportOutOfScopeException(String message) {
        super(message);
    }
}
