package com.hls.transfer.internal;

import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one data scope of spec 027 (Constitution Principle III), shared by lists, details, history panels and the Teacher
 * filter. Admin and Director see everything. A Manager reads a request when the leaving or the receiving School is in
 * scope and raises, changes, cancels or decides only for the leaving School. Anything else is "not found".
 */
@Component
class MovementScope {

    private final ScopeQueries scopes;

    MovementScope(ScopeQueries scopes) {
        this.scopes = scopes;
    }

    ScopeView scope(Caller caller) {
        return scopes.scopeOf(caller.userId(), caller.roles());
    }

    boolean canRead(Caller caller, MovementRequest r) {
        ScopeView scope = scope(caller);
        return scope.orgWide()
                || scope.allowsSchool(r.getFromSchoolId())
                || scope.allowsSchool(r.getToSchoolId())
                || (r.getOutgoingDestinationSchoolId() != null && scope.allowsSchool(r.getOutgoingDestinationSchoolId()));
    }

    boolean canWrite(Caller caller, UUID leavingSchoolId) {
        return scope(caller).allowsSchool(leavingSchoolId);
    }

    /** Refuses with "not found" unless the request is in scope. */
    void requireRead(Caller caller, MovementRequest r) {
        if (!canRead(caller, r)) {
            throw new NotFoundException("Request not found.");
        }
    }

    /** Refuses with "not found" if invisible, and with "forbidden" if only readable (the receiving School alone). */
    void requireWrite(Caller caller, MovementRequest r) {
        requireRead(caller, r);
        if (!canWrite(caller, r.getFromSchoolId())) {
            throw new ForbiddenFieldException("Only the leaving School's Manager, a Director or an Admin can do this.");
        }
    }

    /** Refuses with "not found" unless the School is the caller's to act for. */
    void requireSchool(Caller caller, UUID schoolId, String what) {
        if (!canWrite(caller, schoolId)) {
            throw new NotFoundException(what + " not found.");
        }
    }
}
