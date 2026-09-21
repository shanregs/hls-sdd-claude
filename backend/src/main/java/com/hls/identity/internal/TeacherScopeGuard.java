package com.hls.identity.internal;

import com.hls.identity.api.TeacherScopeQueries;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FR-005/FR-021, spec.md User Story 6: compares the caller's own
 * {@code User.linkedTeacherId} to the target record's teacher id. Unlike
 * {@link ManagerScopeGuard}, this needs no cross-module call — the Teacher's
 * own linkage already lives on this module's own {@link User} record
 * (spec.md Assumptions). Implements the public {@link TeacherScopeQueries}
 * contract so other modules depend on that interface, never on this class.
 */
@Component
public class TeacherScopeGuard implements TeacherScopeQueries {

    private final UserRepository userRepository;
    private final AuthAuditLogger auditLogger;

    public TeacherScopeGuard(UserRepository userRepository, AuthAuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    /**
     * @return true if {@code callerId} is allowed to act on a record owned by
     * {@code targetTeacherId}. A caller with no linked Teacher id yet is denied
     * by default (FR-021) — never granted broad access as a fallback. Every
     * denial writes an ACCESS_DENIED audit entry (FR-013).
     */
    @Override
    public boolean isAllowed(UUID callerId, UUID targetTeacherId) {
        boolean allowed = userRepository.findById(callerId)
                .map(User::getLinkedTeacherId)
                .map(linkedTeacherId -> linkedTeacherId.equals(targetTeacherId))
                .orElse(false);
        if (!allowed) {
            auditLogger.accessDenied(callerId, Role.TEACHER.name(), currentRequestId());
        }
        return allowed;
    }

    private String currentRequestId() {
        return com.hls.CorrelationIdFilter.currentCorrelationId();
    }
}
