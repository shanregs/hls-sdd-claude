package com.hls.school.api;

/**
 * FR-005: raised when a School-Zone reassignment names an assignment row
 * that someone else already ended first. Public (not internal) because
 * callers of {@link ZoneCommands} need to catch it.
 */
public class ZoneAssignmentConflictException extends RuntimeException {

    public ZoneAssignmentConflictException(String message) {
        super(message);
    }
}
