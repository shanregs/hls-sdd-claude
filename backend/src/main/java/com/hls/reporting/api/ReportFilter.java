package com.hls.reporting.api;

/** A filter a report may offer besides the month. */
public enum ReportFilter {
    ZONE,
    SCHOOL,
    TEACHER,
    MANAGER,
    /** A recruitment season label (spec 014c A0); reports that offer it read it from {@link ReportRequest#season()}. */
    SEASON
}
