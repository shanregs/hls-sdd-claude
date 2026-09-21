package com.hls.organization.api;

/**
 * FR-011: raised when a reassignment or end-without-replacement names an
 * assignment row that someone else already ended first. Public (not internal)
 * because callers of {@link AccountabilityCommands} need to catch it.
 */
public class AssignmentConflictException extends RuntimeException {

    public AssignmentConflictException(String message) {
        super(message);
    }
}
