package com.hls.attendance.api.dto;

/**
 * Distinguishes a Teacher's self-mark from a Manager's or Admin's on-behalf
 * mark (FR-002/FR-006/FR-024). {@code ADMIN} is unscoped (any Teacher);
 * {@code MANAGER} is scoped to that Manager's currently-accountable Teachers.
 */
public enum MarkedByRole {
    TEACHER,
    MANAGER,
    ADMIN
}
