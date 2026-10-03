package com.hls.audit.changehistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixChanged;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 2 (FR-002, SC-002): publishing {@code PermissionMatrixChanged} results in one
 * {@code change_history_entry} row; redelivery of the same event id is a no-op; and a real matrix
 * edit propagates to Change History within 5 seconds.
 */
@SpringBootTest
@Testcontainers
class ChangeHistoryEventConsumerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ChangeHistoryEntryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PermissionMatrixService permissionMatrixService;

    private void publishInCommittedTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    @Test
    void consumingAnEventCreatesExactlyOneEntryWithMappedFields() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        publishInCommittedTransaction(new PermissionMatrixChanged(
                eventId,
                actorId,
                Instant.parse("2026-01-01T10:00:00Z"),
                Role.MANAGER,
                PermissionModule.DASHBOARD,
                PermissionAction.VIEW,
                true,
                false));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entry = repository.findBySourceEventId(eventId).orElseThrow();
            assertThat(entry.getActorUserId()).isEqualTo(actorId);
            assertThat(entry.getEntityType()).isEqualTo("PERMISSION_MATRIX");
            assertThat(entry.getEntityId()).isEqualTo("MANAGER.DASHBOARD.VIEW");
            assertThat(entry.getField()).isEqualTo("granted");
            assertThat(entry.getBeforeValue()).isEqualTo("true");
            assertThat(entry.getAfterValue()).isEqualTo("false");
        });
    }

    @Test
    void redeliveringTheSameEventIdIsANoOp() {
        UUID eventId = UUID.randomUUID();
        PermissionMatrixChanged event = new PermissionMatrixChanged(
                eventId,
                UUID.randomUUID(),
                Instant.parse("2026-01-01T10:05:00Z"),
                Role.TEACHER,
                PermissionModule.DASHBOARD,
                PermissionAction.VIEW,
                true,
                true);

        publishInCommittedTransaction(event);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId)).isPresent());

        publishInCommittedTransaction(event);
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long count =
                    repository.findAll().stream().filter(e -> e.getSourceEventId().equals(eventId)).count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void aRealMatrixEditAppearsInChangeHistoryWithinFiveSeconds() {
        UUID actorId = UUID.randomUUID();

        permissionMatrixService.updateGrant(Role.MANAGER, PermissionModule.ACCOUNT_PROFILE, PermissionAction.EDIT, false, actorId);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(repository.findAll().stream()
                        .anyMatch(e -> e.getEntityId().equals("MANAGER.ACCOUNT_PROFILE.EDIT")
                                && e.getActorUserId().equals(actorId)))
                .isTrue());
    }
}
