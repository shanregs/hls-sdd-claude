package com.hls.reporting.api;

/** The Dashboard areas, in display order. */
public enum Area {
    MANAGERS("Managers"),
    SCHOOLS("Schools"),
    RECRUITMENT("Recruitment"),
    TEACHERS("Teachers"),
    ATTENDANCE_LEAVE("Attendance and leave"),
    PAYROLL("Payroll");

    private final String title;

    Area(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
