package com.hls.organization.api;

/**
 * FR-003/FR-004: raised when {@code assignSchoolManager}'s chosen Manager
 * does not currently cover the School's Zone (or the School has no current
 * Zone, or its Zone has no covering Managers yet). Public (not internal)
 * because callers of {@link AccountabilityCommands} need to catch it —
 * distinct from {@link AssignmentConflictException} (research.md §4): this
 * is never retry-worthy as-is, a different choice is required.
 */
public class SchoolManagerNotInZoneException extends RuntimeException {

    public SchoolManagerNotInZoneException(String message) {
        super(message);
    }
}
