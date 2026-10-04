package com.hls.audit.logs;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.changehistory.ChangeHistoryEntry;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
import com.hls.audit.loginhistory.LoginHistoryEntry;
import com.hls.audit.loginhistory.LoginHistoryEntryRepository;
import com.hls.audit.useractivity.UserActivityEntry;
import com.hls.audit.useractivity.UserActivityEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 4 (FR-008): with one row seeded in each of the three tables, the combined query
 * returns all three in {@code occurredAt} order, is filterable by {@code type}, and each row's
 * summary is a short human-readable line (data-model.md's "Audit Log Entry").
 */
@SpringBootTest
@Testcontainers
class AuditLogQueryServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LoginHistoryEntryRepository loginHistoryEntryRepository;

    @Autowired
    private ChangeHistoryEntryRepository changeHistoryEntryRepository;

    @Autowired
    private UserActivityEntryRepository userActivityEntryRepository;

    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @Test
    void combinedQueryReturnsAllThreeInOrderWithSummaries() {
        UUID actor = UUID.randomUUID();
        loginHistoryEntryRepository.save(new LoginHistoryEntry(
                Instant.parse("2026-02-01T10:00:00Z"), UUID.randomUUID(), actor, "98XXXXX001", "PASSWORD", "SIGN_IN_FAILURE", "FAILURE"));
        changeHistoryEntryRepository.save(new ChangeHistoryEntry(
                Instant.parse("2026-02-01T10:01:00Z"),
                UUID.randomUUID(),
                actor,
                "PERMISSION_MATRIX",
                "MANAGER.ATTENDANCE.EDIT",
                "granted",
                "false",
                "true"));
        userActivityEntryRepository.save(new UserActivityEntry(
                Instant.parse("2026-02-01T10:02:00Z"), UUID.randomUUID(), actor, actor, "ACCOUNT_DEACTIVATED", null));

        var page = auditLogQueryService.query(AuditLogQueryService.ALL_TYPES, actor, null, null, PageRequest.of(0, 25));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).extracting(AuditLogEntryView::type).containsExactly("ACTIVITY", "CHANGE", "LOGIN");
        assertThat(page.getContent()).allMatch(e -> e.summary() != null && !e.summary().isBlank());
    }

    @Test
    void filterableByType() {
        UUID actor = UUID.randomUUID();
        loginHistoryEntryRepository.save(new LoginHistoryEntry(
                Instant.parse("2026-02-02T10:00:00Z"), UUID.randomUUID(), actor, "98XXXXX002", "PASSWORD", "SIGN_IN_SUCCESS", "SUCCESS"));
        userActivityEntryRepository.save(new UserActivityEntry(
                Instant.parse("2026-02-02T10:01:00Z"), UUID.randomUUID(), actor, actor, "SESSION_ENDED", null));

        var page = auditLogQueryService.query(java.util.Set.of("LOGIN"), actor, null, null, PageRequest.of(0, 25));

        assertThat(page.getContent()).allMatch(e -> e.type().equals("LOGIN"));
    }
}
