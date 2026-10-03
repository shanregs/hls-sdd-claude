package com.hls.audit.changehistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.audit.api.EntityChanged;
import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 005 research.md section 1: the generic EntityChanged event lands in Change History once. */
class EntityChangedConsumerTest extends IntegrationTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ChangeHistoryEntryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void publish(EntityChanged event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> eventPublisher.publishEvent(event));
    }

    @Test
    void anEntityChangedEventBecomesOneChangeHistoryEntry() {
        UUID eventId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        publish(new EntityChanged(
                eventId, Instant.parse("2026-02-01T10:00:00Z"), actor, "SCHOOL", "s-1", "name", "Old", "New"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entry = repository.findBySourceEventId(eventId).orElseThrow();
            assertThat(entry.getEntityType()).isEqualTo("SCHOOL");
            assertThat(entry.getEntityId()).isEqualTo("s-1");
            assertThat(entry.getField()).isEqualTo("name");
            assertThat(entry.getBeforeValue()).isEqualTo("Old");
            assertThat(entry.getAfterValue()).isEqualTo("New");
            assertThat(entry.getActorUserId()).isEqualTo(actor);
        });
    }

    @Test
    void redeliveringTheSameEventIdIsANoOp() {
        UUID eventId = UUID.randomUUID();
        EntityChanged event = new EntityChanged(
                eventId, Instant.parse("2026-02-01T10:01:00Z"), UUID.randomUUID(), "ZONE", "z-1", "name", null, "North");

        publish(event);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId)).isPresent());
        publish(event);

        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long count = repository.findAll().stream()
                    .filter(e -> e.getSourceEventId().equals(eventId))
                    .count();
            assertThat(count).isEqualTo(1);
        });
    }
}
