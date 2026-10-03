package com.hls.organization.api;

import com.hls.identity.user.Role;
import java.util.Set;
import java.util.UUID;

/**
 * The one shared way to ask which Zones and Schools a user may access (spec 005 FR-021); later
 * modules (attendance, leave, payroll, reports) MUST use this and MUST NOT re-implement scoping.
 * Derived on every call from the current assignments, so changes apply on the next request.
 */
public interface ScopeQueries {

    ScopeView scopeOf(UUID userId, Set<Role> roles);
}
