package com.hls.attendance.api;

/** FR-005: raised when a {@code statusCode} does not correspond to any known, active Attendance Status Code. */
public class UnknownAttendanceStatusCodeException extends RuntimeException {

    private final String code;

    public UnknownAttendanceStatusCodeException(String code) {
        super("Unknown attendance status code: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
