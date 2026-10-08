package com.hls.designation.api;

import java.util.UUID;

/** The employee-id registry: one id per person, unique across Managers and Teachers together (spec 005a FR-007). */
public interface EmployeeIds {

    enum PersonKind {
        TEACHER,
        MANAGER
    }

    /**
     * Validates the format (1 to 20 letters, digits or hyphens, after trimming) and claims the id for the person,
     * in the caller's transaction. A null or blank id releases the person's current claim. A duplicate (ignoring
     * capital letters and spaces at the ends) is a conflict naming the person who holds it. Returns the trimmed id,
     * or null when released.
     */
    String claim(PersonKind kind, UUID personId, String personName, String employeeId);
}
