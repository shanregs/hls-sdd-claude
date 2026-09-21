package com.hls.identity.api;

import java.util.UUID;

/**
 * Public surface for FR-004/FR-019/FR-020's Manager scoping check. Any future
 * module (Attendance, SchoolBilling, Substitution, ...) that owns School/Teacher-
 * scoped records depends on this interface, never on {@code identity.internal}
 * directly (Constitution Principle V).
 */
public interface ManagerScopeQueries {

    boolean isAllowedForSchool(UUID callerId, UUID schoolId);

    boolean isAllowedForTeacher(UUID callerId, UUID teacherId);
}
