package com.hls.identity.api;

import java.util.UUID;

/**
 * Public surface for FR-005/FR-021's Teacher self-scoping check. Any future
 * module (Attendance, Payroll, Training, Expense) that owns Teacher-scoped
 * records depends on this interface, never on {@code identity.internal}
 * directly (Constitution Principle V).
 */
public interface TeacherScopeQueries {

    /** @return true if {@code callerId} (a Teacher) may act on a record owned by {@code targetTeacherId}. */
    boolean isAllowed(UUID callerId, UUID targetTeacherId);
}
