package com.hls.identity;

import com.hls.identity.internal.*;
import com.hls.organization.api.AccountabilityQueries;
import com.hls.organization.api.dto.AccountabilityAnswer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for FR-019/FR-020 (User Story 3): in-scope allowed, out-of-scope
 * denied, Organization-call-failure denied, Organization-call-timeout denied —
 * and all three denial paths are indistinguishable to the caller (they all just
 * return {@code false}).
 */
class ManagerScopeGuardTest {

    private final AuthAuditLogger auditLogger = mock(AuthAuditLogger.class);
    private final IdentityProperties properties = new IdentityProperties();

    @Test
    void isAllowedForSchool_whenCallerIsCurrentManager_returnsTrue() {
        UUID managerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        AccountabilityQueries organization = mock(AccountabilityQueries.class);
        when(organization.currentManagerForSchool(schoolId))
                .thenReturn(AccountabilityAnswer.currentManager(managerId));

        ManagerScopeGuard guard = new ManagerScopeGuard(organization, auditLogger, properties);

        assertThat(guard.isAllowedForSchool(managerId, schoolId)).isTrue();
        verify(auditLogger, never()).accessDenied(any(), any(), any());
    }

    @Test
    void isAllowedForSchool_whenCallerIsNotTheAssignedManager_returnsFalseAndAudits() {
        UUID assignedManager = UUID.randomUUID();
        UUID otherManager = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        AccountabilityQueries organization = mock(AccountabilityQueries.class);
        when(organization.currentManagerForSchool(schoolId))
                .thenReturn(AccountabilityAnswer.currentManager(assignedManager));

        ManagerScopeGuard guard = new ManagerScopeGuard(organization, auditLogger, properties);

        assertThat(guard.isAllowedForSchool(otherManager, schoolId)).isFalse();
        verify(auditLogger).accessDenied(otherManager, Role.MANAGER.name(), "unknown");
    }

    @Test
    void isAllowedForSchool_whenUnassigned_returnsFalse() {
        UUID managerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        AccountabilityQueries organization = mock(AccountabilityQueries.class);
        when(organization.currentManagerForSchool(schoolId)).thenReturn(AccountabilityAnswer.unassigned());

        ManagerScopeGuard guard = new ManagerScopeGuard(organization, auditLogger, properties);

        assertThat(guard.isAllowedForSchool(managerId, schoolId)).isFalse();
    }

    // FR-019: an outright failure from Organization denies, exactly like out-of-scope.
    @Test
    void isAllowedForSchool_whenOrganizationCallThrows_returnsFalse() {
        UUID managerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        AccountabilityQueries organization = mock(AccountabilityQueries.class);
        when(organization.currentManagerForSchool(schoolId)).thenThrow(new RuntimeException("boom"));

        ManagerScopeGuard guard = new ManagerScopeGuard(organization, auditLogger, properties);

        assertThat(guard.isAllowedForSchool(managerId, schoolId)).isFalse();
    }

    // FR-019/SC-007: exceeding the configured bound denies, never blocks indefinitely.
    @Test
    void isAllowedForSchool_whenOrganizationCallExceedsTimeout_returnsFalse() {
        UUID managerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        properties.setManagerScopeCheckTimeoutMs(100);
        AccountabilityQueries organization = mock(AccountabilityQueries.class);
        when(organization.currentManagerForSchool(schoolId)).thenAnswer(invocation -> {
            Thread.sleep(1000);
            return AccountabilityAnswer.currentManager(managerId);
        });

        ManagerScopeGuard guard = new ManagerScopeGuard(organization, auditLogger, properties);

        long start = System.currentTimeMillis();
        boolean allowed = guard.isAllowedForSchool(managerId, schoolId);
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(allowed).isFalse();
        assertThat(elapsedMs).isLessThan(900); // denied around the 100ms bound, not after the 1000ms call
    }
}
