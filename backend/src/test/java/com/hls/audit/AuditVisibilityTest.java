package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.audit.api.EntityChanged;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 005 research.md section 16: Audit screens hide master-data and salary entries from callers
 * without the matching VIEW grant (System never sees school or teacher data).
 */
class AuditVisibilityTest extends IntegrationTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ChangeHistoryEntryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void publish(String entityType, String entityId) {
        UUID eventId = UUID.randomUUID();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(s -> eventPublisher.publishEvent(new EntityChanged(
                        eventId, Instant.now(), UUID.randomUUID(), entityType, entityId, "f", "a", "b")));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findBySourceEventId(eventId)).isPresent());
    }

    @Test
    void adminSeesEverythingAndSystemSeesNoMasterDataOrSalary() {
        String marker = "vis-" + UUID.randomUUID();
        publish("SCHOOL", marker + "-school");
        publish("TEACHER", marker + "-teacher");
        publish("TEACHER_SALARY", marker + "-salary");
        publish("PERMISSION_MATRIX", marker + "-matrix");
        String admin = signInAs(Role.ADMIN).token();
        String system = signInAs(Role.SYSTEM).token();

        for (String path : new String[] {
            "/api/v1/audit/change-history?size=100", "/api/v1/audit/logs?type=CHANGE&size=100"
        }) {
            String adminBody = get(path, admin).body();
            assertThat(adminBody).contains(marker + "-school", marker + "-teacher", marker + "-salary");
            String systemBody = get(path, system).body();
            assertThat(systemBody).doesNotContain(marker + "-school", marker + "-teacher", marker + "-salary");
        }
        assertThat(get("/api/v1/audit/change-history?size=100", system).body()).contains(marker + "-matrix");
    }

    @Test
    void totalsAndCsvExportFollowTheSameFilter() {
        String marker = "vis2-" + UUID.randomUUID();
        publish("SCHOOL", marker);
        String admin = signInAs(Role.ADMIN).token();
        String system = signInAs(Role.SYSTEM).token();
        String range = "from=2020-01-01T00:00:00Z&to=2099-01-01T00:00:00Z";

        assertThat(get("/api/v1/audit/change-history/export?" + range, admin).body())
                .contains(marker);
        assertThat(get("/api/v1/audit/change-history/export?" + range, system).body())
                .doesNotContain(marker);
        assertThat(total(get("/api/v1/audit/change-history?entityId=" + marker, system)))
                .isZero();
        assertThat(total(get("/api/v1/audit/change-history?entityId=" + marker, admin)))
                .isEqualTo(1);
    }
}
