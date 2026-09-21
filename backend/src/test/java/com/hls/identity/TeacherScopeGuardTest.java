package com.hls.identity;

import com.hls.identity.internal.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit coverage for FR-005/FR-021 (User Story 6). */
class TeacherScopeGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthAuditLogger auditLogger = mock(AuthAuditLogger.class);
    private final TeacherScopeGuard guard = new TeacherScopeGuard(userRepository, auditLogger);

    @Test
    void isAllowed_whenTargetIsCallersOwnLinkedTeacher_returnsTrue() {
        UUID teacherId = UUID.randomUUID();
        User caller = new User(UUID.randomUUID(), "Teacher", "+919800000060", Set.of(Role.TEACHER), Instant.now());
        caller.setLinkedTeacherId(teacherId);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        assertThat(guard.isAllowed(caller.getId(), teacherId)).isTrue();
        verify(auditLogger, never()).accessDenied(any(), any(), any());
    }

    @Test
    void isAllowed_whenTargetIsAnotherTeacher_returnsFalseAndAudits() {
        UUID callerId = UUID.randomUUID();
        UUID otherTeacherId = UUID.randomUUID();
        User caller = new User(callerId, "Teacher", "+919800000061", Set.of(Role.TEACHER), Instant.now());
        caller.setLinkedTeacherId(UUID.randomUUID()); // caller's own teacher record, not otherTeacherId
        when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));

        assertThat(guard.isAllowed(callerId, otherTeacherId)).isFalse();
        verify(auditLogger).accessDenied(callerId, Role.TEACHER.name(), "unknown");
    }

    // FR-021: missing linkage is denied by default, never treated as broad access.
    @Test
    void isAllowed_whenCallerHasNoLinkedTeacherIdYet_returnsFalse() {
        UUID callerId = UUID.randomUUID();
        User caller = new User(callerId, "New Teacher", "+919800000062", Set.of(Role.TEACHER), Instant.now());
        // linkedTeacherId intentionally left null
        when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));

        assertThat(guard.isAllowed(callerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void isAllowed_whenCallerDoesNotExist_returnsFalse() {
        UUID callerId = UUID.randomUUID();
        when(userRepository.findById(callerId)).thenReturn(Optional.empty());

        assertThat(guard.isAllowed(callerId, UUID.randomUUID())).isFalse();
    }
}
