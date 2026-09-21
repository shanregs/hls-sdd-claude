package com.hls.identity.internal;

import com.hls.identity.api.ManagerScopeQueries;
import com.hls.organization.api.AccountabilityQueries;
import com.hls.organization.api.dto.AccountabilityAnswer;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.*;

/**
 * FR-004/FR-019/FR-020: calls Organization's real {@link AccountabilityQueries}
 * with a 2-second timeout budget. Denies identically on failure, timeout, or
 * genuine out-of-scope (FR-020) — the caller can never tell which case
 * happened, only that access was denied.
 *
 * <p>Until 2026-09-22 this called a local in-module stand-in
 * (research.md §6/§7) because Organization (spec 003) had no code yet; T032
 * of specs/003-organization-scoping/tasks.md retired that stand-in once the
 * real module existed, with no change needed to this class's own logic since
 * {@code AccountabilityAnswer}'s shape was locked to match it exactly.
 */
@Component
public class ManagerScopeGuard implements ManagerScopeQueries {

    private final AccountabilityQueries organization;
    private final AuthAuditLogger auditLogger;
    private final IdentityProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ManagerScopeGuard(AccountabilityQueries organization, AuthAuditLogger auditLogger,
                              IdentityProperties properties) {
        this.organization = organization;
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    @Override
    public boolean isAllowedForSchool(UUID callerId, UUID schoolId) {
        return checkAndAudit(callerId, () -> organization.currentManagerForSchool(schoolId), callerId);
    }

    @Override
    public boolean isAllowedForTeacher(UUID callerId, UUID teacherId) {
        return checkAndAudit(callerId, () -> organization.currentManagerForTeacher(teacherId), callerId);
    }

    private boolean checkAndAudit(UUID callerId, Callable<AccountabilityAnswer> call, UUID auditActor) {
        boolean allowed = isAllowed(callerId, call);
        if (!allowed) {
            auditLogger.accessDenied(auditActor, Role.MANAGER.name(), currentRequestId());
        }
        return allowed;
    }

    /**
     * FR-019: bounded-timeout call — the contract holds regardless of whether
     * Organization is in-process (today) or a separate deployment later
     * (research.md §6).
     */
    private boolean isAllowed(UUID callerId, Callable<AccountabilityAnswer> call) {
        Future<AccountabilityAnswer> future = executor.submit(call);
        try {
            AccountabilityAnswer answer = future.get(
                    properties.getManagerScopeCheckTimeoutMs(), TimeUnit.MILLISECONDS);
            return answer.state() == AccountabilityAnswer.State.CURRENT_MANAGER
                    && callerId.equals(answer.managerId());
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            // FR-019/FR-020: any failure mode denies, with no distinction visible to the caller.
            future.cancel(true);
            return false;
        }
    }

    private String currentRequestId() {
        return com.hls.CorrelationIdFilter.currentCorrelationId();
    }
}
