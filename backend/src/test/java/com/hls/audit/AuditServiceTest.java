package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.audit.api.dto.AuditAction;
import com.hls.audit.api.dto.AuditEntryView;
import com.hls.audit.api.dto.AuditRecordRequest;
import com.hls.audit.internal.AuditEntry;
import com.hls.audit.internal.AuditEntryRepository;
import com.hls.audit.internal.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for FR-002/003/004/007/010/011 (User Stories 1-2's service-level
 * rules). The repository is mocked; the real DB constraint (sequence_no
 * ordering, FR-011) and end-to-end HTTP flow live in {@code AuditIntegrationTest}.
 */
class AuditServiceTest {

    private AuditEntryRepository repository;
    private Clock clock;
    private AuditService service;

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(AuditEntryRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AuditService(repository, clock);
    }

    // ---- User Story 1: record() ----------------------------------------------------------

    @Test
    void record_assignsIdAndOccurredAt_neverTrustingTheCaller() {
        UUID actorUserId = UUID.randomUUID();
        AuditRecordRequest request = new AuditRecordRequest(
                "attendance", "AttendanceRecord", "attendance-record-42", AuditAction.UPDATED,
                "status: Present -> Leave", "{\"status\":\"Present\"}", "{\"status\":\"Leave\"}",
                actorUserId, "MANAGER", "req-abc123");

        UUID entryId = service.record(request);

        assertThat(entryId).isNotNull();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getId().equals(entryId)
                        && entry.getSourceModule().equals("attendance")
                        && entry.getEntityType().equals("AttendanceRecord")
                        && entry.getEntityId().equals("attendance-record-42")
                        && entry.getAction() == AuditAction.UPDATED
                        && entry.getBeforeValue().equals("{\"status\":\"Present\"}")
                        && entry.getAfterValue().equals("{\"status\":\"Leave\"}")
                        && entry.getActorUserId().equals(actorUserId)
                        && entry.getActorRole().equals("MANAGER")
                        && entry.getOccurredAt().equals(NOW)
                        && entry.getRequestId().equals("req-abc123")));
    }

    @Test
    void record_createdAction_storesNullBeforeValue() {
        AuditRecordRequest request = new AuditRecordRequest(
                "expense", "Expense", "expense-1", AuditAction.CREATED,
                "expense claim submitted", null, "{\"amount\":500}",
                UUID.randomUUID(), "MANAGER", null);

        service.record(request);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getAction() == AuditAction.CREATED && entry.getBeforeValue() == null));
    }

    @Test
    void record_withNullActorUserId_isRejected() {
        AuditRecordRequest request = new AuditRecordRequest(
                "payroll", "Payslip", "payslip-1", AuditAction.CREATED,
                "payslip generated", null, "{\"netSalary\":14400}",
                null, "ADMIN", null);

        assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    // ---- User Story 2: history() ---------------------------------------------------------

    @Test
    void history_returnsEntriesOrderedBySequenceNo() {
        AuditEntry first = new AuditEntry(UUID.randomUUID(), "attendance", "AttendanceRecord", "att-1",
                AuditAction.CREATED, "created", null, "{}", UUID.randomUUID(), "MANAGER", NOW, null);
        AuditEntry second = new AuditEntry(UUID.randomUUID(), "attendance", "AttendanceRecord", "att-1",
                AuditAction.UPDATED, "updated", "{}", "{\"x\":1}", UUID.randomUUID(), "MANAGER", NOW, null);
        when(repository.findByEntityTypeAndEntityIdOrderBySequenceNoAsc("AttendanceRecord", "att-1"))
                .thenReturn(List.of(first, second));

        List<AuditEntryView> history = service.history("AttendanceRecord", "att-1");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).action()).isEqualTo(AuditAction.CREATED);
        assertThat(history.get(1).action()).isEqualTo(AuditAction.UPDATED);
    }

    // ---- User Story 3: a correction appends, it never overwrites -------------------------

    @Test
    void correction_isARecordCallProducingASeparateNewEntry_notAnUpdateToTheOriginal() {
        UUID actorUserId = UUID.randomUUID();
        AuditRecordRequest created = new AuditRecordRequest(
                "expense", "Expense", "expense-3", AuditAction.CREATED,
                "expense claim submitted", null, "{\"amount\":500}", actorUserId, "MANAGER", null);
        AuditRecordRequest corrected = new AuditRecordRequest(
                "expense", "Expense", "expense-3", AuditAction.CORRECTED,
                "amount: 500 -> 5000 (typo fix)", "{\"amount\":500}", "{\"amount\":5000}", actorUserId, "ADMIN", null);

        UUID createdId = service.record(created);
        UUID correctedId = service.record(corrected);

        // Two separate save() calls, two separate ids — AuditEntryRepository has no
        // update method for the first call's result to have been passed to instead (FR-006).
        assertThat(correctedId).isNotEqualTo(createdId);
        verify(repository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void history_forNeverAuditedRecord_returnsEmptyListNotError() {
        when(repository.findByEntityTypeAndEntityIdOrderBySequenceNoAsc("Expense", "never-audited"))
                .thenReturn(List.of());

        List<AuditEntryView> history = service.history("Expense", "never-audited");

        assertThat(history).isEmpty();
    }
}
