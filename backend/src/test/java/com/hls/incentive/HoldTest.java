package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.incentive.internal.ReconcileJob;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 030 US5 (T033): an approved incentive waits while its MoU does not count as approved, and is never created twice. */
class HoldTest extends IncentiveTestBase {

    @Autowired
    private ReconcileJob reconcile;

    private String admin;

    @BeforeEach
    void clean() {
        resetRuns();
        admin = adminToken();
    }

    private record Case(Mou mou, UUID id, UUID managerId) {
        UUID schoolId() {
            return mou.f().schoolId();
        }
    }

    /** An approved incentive of a paid Manager whose MoU has a document (so a new version resets it). */
    private Case approvedIncentive() {
        Mou m = mou();
        makePayable(m.admin(), m.f().manager(), "30000");
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        approveMou(m);
        UUID id = idOf(awaitIncentive(m.f().schoolId()));
        long version = ((Number) incentiveOf(m.f().schoolId()).get("version")).longValue();
        Resp approved = post(API + "/" + id + "/approve", m.director().token(), Map.of("version", version));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        return new Case(m, id, m.f().manager().managerId());
    }

    private String status(Case c) {
        return (String) incentiveOf(c.schoolId()).get("status");
    }

    private void awaitStatus(Case c, String status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> status.equals(status(c)));
    }

    private String adjustmentStatuses(Case c) {
        return String.join(",", db.queryForList(
                "select status from payroll_adjustment where source_ref = ? order by created_at, id", String.class, "INCENTIVE:" + c.id()));
    }

    private void changeMou(Case c) {
        assertThat(upload(c.mou().admin(), c.mou().contractId(), "v-new-" + UUID.randomUUID() + ".pdf", PDF).status()).isEqualTo(201);
    }

    private void approveMouAgain(Case c) {
        Resp resp = approve(c.mou().director().token(), c.mou().contractId());
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    private java.math.BigDecimal adjustments(UUID run, Case c) {
        Map<String, Object> line = lineOf(admin, run, c.managerId());
        assertThat(line).isNotNull();
        Object value = line.get("adjustments");
        return new java.math.BigDecimal(value == null ? "0" : (String) value);
    }

    @Test
    void aMoUThatStopsCountingHoldsTheIncentiveAndTheNextRunDoesNotTakeIt() {
        Case c = approvedIncentive();
        assertThat(adjustmentStatuses(c)).isEqualTo("WAITING");

        changeMou(c);
        awaitStatus(c, "HELD");

        assertThat(adjustmentStatuses(c)).isEqualTo("WITHDRAWN");
        assertThat(incentiveOf(c.schoolId()).get("hold_reason")).isEqualTo("MoU not approved");
        UUID run = startAndCompute(admin);
        assertThat(adjustments(run, c)).isEqualByComparingTo("0.00");
        assertThat(eventKinds(c.id())).containsExactly("EARNED", "APPROVED", "OFFERED", "HELD");
    }

    @Test
    void approvingTheMoUAgainReleasesItAndTheNextRunTakesItWithoutASecondIncentive() {
        Case c = approvedIncentive();
        changeMou(c);
        awaitStatus(c, "HELD");

        approveMouAgain(c);
        awaitStatus(c, "APPROVED");

        assertThat(adjustmentStatuses(c)).isEqualTo("WITHDRAWN,WAITING");
        assertThat(incentiveRows(c.schoolId())).isEqualTo(1);
        assertThat(incentiveOf(c.schoolId()).get("hold_reason")).isNull();
        UUID run = startAndCompute(admin);
        assertThat(adjustments(run, c)).isEqualByComparingTo("5000.00");
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(approveRun(c.mou().director().token(), run).status()).isEqualTo(200);
        assertThat(status(c)).isEqualTo("PAID_IN_RUN");
        assertThat(eventKinds(c.id())).containsExactly("EARNED", "APPROVED", "OFFERED", "HELD", "OFFERED", "RELEASED", "APPLIED");
    }

    @Test
    void aRunAwaitingApprovalKeepsTheIncentiveWithAFlagAndTheDirectorIsToldOnce() {
        Case c = approvedIncentive();
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);

        changeMou(c);
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> Boolean.TRUE.equals(incentiveOf(c.schoolId()).get("mou_changed")));

        assertThat(status(c)).isEqualTo("APPROVED");
        assertThat(adjustmentStatuses(c)).isEqualTo("WAITING");
        assertThat(eventKinds(c.id())).contains("MOU_CHANGED");
        settle();
        assertThat(notifications(c.mou().director().userId(), "INCENTIVE_MOU_CHANGED")).isEqualTo(1);
        // a second look changes nothing and writes no second entry
        reconcile.runNow();
        assertThat(eventKinds(c.id()).stream().filter("MOU_CHANGED"::equals).count()).isEqualTo(1);

        // the Director decides the run; the incentive was in it, so it is paid and never changes
        assertThat(approveRun(c.mou().director().token(), run).status()).isEqualTo(200);
        assertThat(status(c)).isEqualTo("PAID_IN_RUN");
        assertThat(incentiveOf(c.schoolId()).get("mou_changed")).isEqualTo(false);
    }

    @Test
    void anIncentiveAlreadyPaidNeverChangesWhenTheMoUChangesLater() {
        Case c = approvedIncentive();
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(approveRun(c.mou().director().token(), run).status()).isEqualTo(200);
        assertThat(status(c)).isEqualTo("PAID_IN_RUN");

        changeMou(c);
        settle();
        reconcile.runNow();

        assertThat(status(c)).isEqualTo("PAID_IN_RUN");
        assertThat(adjustmentStatuses(c)).isEqualTo("APPLIED");
        assertThat(incentiveRows(c.schoolId())).isEqualTo(1);
    }

    @Test
    void theDailyJobHoldsAndReleasesInBothDirectionsWhenNoEventArrived() {
        Case c = approvedIncentive();
        db.update("update mou_approval set status = 'PENDING', decided_by = null, decided_at = null where contract_id = ?", c.mou().contractId());

        ReconcileJob.Result held = reconcile.runNow();

        assertThat(held.held()).isGreaterThanOrEqualTo(1);
        assertThat(status(c)).isEqualTo("HELD");
        assertThat(adjustmentStatuses(c)).isEqualTo("WITHDRAWN");

        db.update("update mou_approval set status = 'APPROVED', decided_by = ?, decided_at = now() where contract_id = ?", c.mou().director().userId(), c.mou().contractId());
        ReconcileJob.Result released = reconcile.runNow();

        assertThat(released.released()).isGreaterThanOrEqualTo(1);
        assertThat(status(c)).isEqualTo("APPROVED");
        assertThat(adjustmentStatuses(c)).isEqualTo("WITHDRAWN,WAITING");
        reconcile.runNow();
        assertThat(adjustmentStatuses(c)).isEqualTo("WITHDRAWN,WAITING");
    }
}
