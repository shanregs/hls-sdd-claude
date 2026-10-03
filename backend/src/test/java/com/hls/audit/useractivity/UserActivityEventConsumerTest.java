package com.hls.audit.useractivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.activity.AccountLockChanged;
import com.hls.identity.activity.PasswordResetCompleted;
import com.hls.identity.activity.PasswordResetRequested;
import com.hls.identity.activity.SessionEnded;
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
 * User Story 3 (FR-003): each of the five lifecycle events maps to the correct {@code action}
 * value in {@code user_activity_entry} and dedups on {@code sourceEventId} redelivery.
 * {@code ACCOUNT_REACTIVATED} is reserved (data-model.md) — this spec never publishes
 * {@code AccountActivationChanged(active=true)}, so it is exercised here directly (the mapping
 * logic itself, not a real caller) rather than left completely untested.
 */
@SpringBootTest
@Testcontainers
class UserActivityEventConsumerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserActivityEntryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void publishInCommittedTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    @Test
    void passwordResetRequestedMapsCorrectly() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        publishInCommittedTransaction(
                new PasswordResetRequested(eventId, Instant.parse("2026-01-01T00:00:00Z"), userId, userId));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId).orElseThrow().getAction())
                        .isEqualTo("PASSWORD_RESET_REQUESTED"));
    }

    @Test
    void passwordResetCompletedMapsCorrectly() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        publishInCommittedTransaction(
                new PasswordResetCompleted(eventId, Instant.parse("2026-01-01T00:01:00Z"), userId, userId));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId).orElseThrow().getAction())
                        .isEqualTo("PASSWORD_RESET_COMPLETED"));
    }

    @Test
    void sessionEndedMapsCorrectlyWithDetail() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        publishInCommittedTransaction(
                new SessionEnded(eventId, Instant.parse("2026-01-01T00:02:00Z"), userId, userId, sessionId));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entry = repository.findBySourceEventId(eventId).orElseThrow();
            assertThat(entry.getAction()).isEqualTo("SESSION_ENDED");
            assertThat(entry.getDetail()).contains(sessionId.toString());
        });
    }

    @Test
    void accountLockChangedMapsToLockedAndUnlocked() {
        UUID lockedEventId = UUID.randomUUID();
        UUID unlockedEventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        publishInCommittedTransaction(
                new AccountLockChanged(lockedEventId, Instant.parse("2026-01-01T00:03:00Z"), null, userId, true));
        publishInCommittedTransaction(
                new AccountLockChanged(unlockedEventId, Instant.parse("2026-01-01T00:04:00Z"), userId, userId, false));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(repository.findBySourceEventId(lockedEventId).orElseThrow().getAction())
                    .isEqualTo("ACCOUNT_LOCKED");
            assertThat(repository.findBySourceEventId(unlockedEventId).orElseThrow().getAction())
                    .isEqualTo("ACCOUNT_UNLOCKED");
        });
    }

    @Test
    void accountActivationChangedMapsToDeactivatedAndReactivated() {
        UUID deactivatedEventId = UUID.randomUUID();
        UUID reactivatedEventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        publishInCommittedTransaction(new AccountActivationChanged(
                deactivatedEventId, Instant.parse("2026-01-01T00:05:00Z"), null, userId, false));
        publishInCommittedTransaction(new AccountActivationChanged(
                reactivatedEventId, Instant.parse("2026-01-01T00:06:00Z"), null, userId, true));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(repository.findBySourceEventId(deactivatedEventId).orElseThrow().getAction())
                    .isEqualTo("ACCOUNT_DEACTIVATED");
            assertThat(repository.findBySourceEventId(reactivatedEventId).orElseThrow().getAction())
                    .isEqualTo("ACCOUNT_REACTIVATED");
        });
    }

    @Test
    void redeliveringTheSameEventIdIsANoOp() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PasswordResetRequested event =
                new PasswordResetRequested(eventId, Instant.parse("2026-01-01T00:07:00Z"), userId, userId);

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
}
