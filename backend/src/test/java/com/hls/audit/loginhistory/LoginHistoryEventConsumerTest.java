package com.hls.audit.loginhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryRecorded;
import com.hls.identity.loginhistory.LoginMethod;
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
 * User Story 1 (FR-001): publishing {@code LoginHistoryRecorded} results in exactly one
 * {@code login_history_entry} row, and redelivery of the same event id is a no-op (dedup via
 * {@code source_event_id}, data-model.md's validation rule).
 */
@SpringBootTest
@Testcontainers
class LoginHistoryEventConsumerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LoginHistoryEntryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * {@code @ApplicationModuleListener} fires after commit (research.md §1) — publishing outside
     * a transaction never invokes it at all, so every publish in this test goes through a
     * transaction that actually commits.
     */
    private void publishInCommittedTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    @Test
    void consumingAnEventCreatesExactlyOneEntry() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");

        publishInCommittedTransaction(new LoginHistoryRecorded(
                eventId, occurredAt, userId, "98XXXXX001", LoginMethod.PASSWORD, LoginEventType.SIGN_IN_SUCCESS, "SUCCESS"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entry = repository.findBySourceEventId(eventId).orElseThrow();
            assertThat(entry.getUserId()).isEqualTo(userId);
            assertThat(entry.getPhoneMasked()).isEqualTo("98XXXXX001");
            assertThat(entry.getMethod()).isEqualTo("PASSWORD");
            assertThat(entry.getEventType()).isEqualTo("SIGN_IN_SUCCESS");
            assertThat(entry.getOutcome()).isEqualTo("SUCCESS");
        });
    }

    @Test
    void redeliveringTheSameEventIdIsANoOp() {
        UUID eventId = UUID.randomUUID();
        LoginHistoryRecorded event = new LoginHistoryRecorded(
                eventId,
                Instant.parse("2026-01-01T10:05:00Z"),
                UUID.randomUUID(),
                "98XXXXX002",
                LoginMethod.OTP,
                LoginEventType.SIGN_IN_SUCCESS,
                "SUCCESS");

        publishInCommittedTransaction(event);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId)).isPresent());

        publishInCommittedTransaction(event);
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long count = repository.findAll().stream()
                    .filter(e -> e.getSourceEventId().equals(eventId))
                    .count();
            assertThat(count).isEqualTo(1);
        });
    }
}
