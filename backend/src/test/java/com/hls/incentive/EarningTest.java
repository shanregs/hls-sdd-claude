package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.incentive.internal.EarningService;
import com.hls.incentive.internal.ReconcileJob;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 030 US1 (T015): when a School's approved MoU earns its owner an incentive, and when it does not. */
class EarningTest extends IncentiveTestBase {

    @Autowired
    private EarningService earning;

    @Autowired
    private ReconcileJob reconcile;

    @AfterEach
    void restore() {
        db.execute("alter table incentive drop constraint if exists ck_test_blocked");
        setAmount(adminToken(), 5000);
    }

    private static LocalDate businessToday() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void approvingTheMoUEarnsTheProspectOwnerOneIncentive() {
        Mou m = mou();
        UUID owner = m.f().manager().signed().userId();
        wonProspect(m.f().zoneId(), m.f().schoolId(), owner, m.adminUserId());

        approveMou(m);
        Map<String, Object> row = awaitIncentive(m.f().schoolId());

        assertThat(row.get("status")).isEqualTo("PENDING_APPROVAL");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("5000.00");
        assertThat(row.get("earner_manager_id")).isEqualTo(m.f().manager().managerId());
        assertThat(row.get("earner_user_id")).isEqualTo(owner);
        assertThat(row.get("zone_id")).isEqualTo(m.f().zoneId());
        assertThat(row.get("contract_id")).isEqualTo(m.contractId());
        assertThat(((java.sql.Date) row.get("closure_date")).toLocalDate()).isEqualTo(businessToday());
        assertThat(eventKinds(idOf(row))).containsExactly("EARNED");
    }

    @Test
    void aDuplicateLookAndAReApprovalAfterADocumentChangeKeepOneIncentive() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        upload(m.admin(), m.contractId(), "v1.pdf", PDF);
        approveMou(m);
        Map<String, Object> first = awaitIncentive(m.f().schoolId());

        assertThat(earning.earn(m.f().schoolId())).isEqualTo(EarningService.Outcome.ALREADY_EARNED);

        // a new document resets the MoU to pending; approving it again raises the event again
        assertThat(upload(m.admin(), m.contractId(), "v2.pdf", PDF).status()).isEqualTo(201);
        assertThat(approve(m.director().token(), m.contractId()).status()).isEqualTo(200);
        settle();

        assertThat(incentiveRows(m.f().schoolId())).isEqualTo(1);
        assertThat(incentiveOf(m.f().schoolId()).get("id")).isEqualTo(first.get("id"));
        assertThat(eventKinds(idOf(first))).containsExactly("EARNED");
    }

    @Test
    void aSchoolWithNoWonProspectIsListedAsNoEarner() {
        Mou m = mou();
        approveMou(m);

        Map<String, Object> row = awaitNoEarner(m.f().schoolId());

        assertThat(row.get("reason")).isEqualTo("NO_PROSPECT");
        assertThat(incentiveOf(m.f().schoolId())).isNull();
    }

    @Test
    void anOwnerWhoIsTheDirectorIsNotAPayrollManager() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.director().userId(), m.adminUserId());
        approveMou(m);

        Map<String, Object> row = awaitNoEarner(m.f().schoolId());

        assertThat(row.get("reason")).isEqualTo("OWNER_NOT_MANAGER");
        assertThat((String) row.get("detail")).startsWith("Owner: ");
        assertThat(incentiveOf(m.f().schoolId())).isNull();
    }

    @Test
    void anOwnerWhoHasLeftEarnsNothingAndALaterLookWithAnActiveOwnerCreatesTheIncentive() {
        Mou m = mou();
        UUID owner = m.f().manager().signed().userId();
        wonProspect(m.f().zoneId(), m.f().schoolId(), owner, m.adminUserId());
        db.update("update manager set active = false where id = ?", m.f().manager().managerId());
        approveMou(m);

        Map<String, Object> row = awaitNoEarner(m.f().schoolId());
        assertThat(row.get("reason")).isEqualTo("NO_OWNER");
        assertThat(incentiveOf(m.f().schoolId())).isNull();

        db.update("update manager set active = true where id = ?", m.f().manager().managerId());
        assertThat(earning.earn(m.f().schoolId())).isEqualTo(EarningService.Outcome.EARNED);
        assertThat(incentiveOf(m.f().schoolId())).isNotNull();
        assertThat(noEarnerOf(m.f().schoolId()).get("resolved_at")).isNotNull();
    }

    @Test
    void aMoURecordedBeforeTheApprovalStepHasNoIncentiveAndShowsAsExcluded() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        makeLegacy(m.contractId());

        assertThat(earning.earn(m.f().schoolId())).isEqualTo(EarningService.Outcome.NO_EARNER);

        assertThat(noEarnerOf(m.f().schoolId()).get("reason")).isEqualTo("APPROVED_BEFORE_APPROVAL_STEP");
        assertThat(incentiveOf(m.f().schoolId())).isNull();
    }

    @Test
    void anApprovalBeforeGoLiveEarnsNothing() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        db.update("update incentive_program set starts_at = now() + interval '1 hour'");
        try {
            approveMou(m);
            Map<String, Object> row = awaitNoEarner(m.f().schoolId());
            assertThat(row.get("reason")).isEqualTo("BEFORE_GO_LIVE");
            assertThat(incentiveOf(m.f().schoolId())).isNull();
        } finally {
            db.update("update incentive_program set starts_at = now() - interval '10 years'");
        }
    }

    @Test
    void anAmountChangeAffectsOnlyNewIncentives() {
        Mou a = mou();
        wonProspect(a.f().zoneId(), a.f().schoolId(), a.f().manager().signed().userId(), a.adminUserId());
        approveMou(a);
        Map<String, Object> before = awaitIncentive(a.f().schoolId());

        assertThat(setAmount(adminToken(), 7000).status()).isEqualTo(200);
        Mou b = mou();
        wonProspect(b.f().zoneId(), b.f().schoolId(), b.f().manager().signed().userId(), b.adminUserId());
        approveMou(b);
        Map<String, Object> after = awaitIncentive(b.f().schoolId());

        assertThat((BigDecimal) before.get("amount")).isEqualByComparingTo("5000.00");
        assertThat((BigDecimal) after.get("amount")).isEqualByComparingTo("7000.00");
        assertThat((BigDecimal) incentiveOf(a.f().schoolId()).get("amount")).isEqualByComparingTo("5000.00");
    }

    @Test
    void theMoUApprovalSucceedsEvenWhenTheIncentiveCannotBeWrittenAndTheDailyJobRepairsIt() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        db.execute("alter table incentive add constraint ck_test_blocked check (false) not valid");
        try {
            approveMou(m);
            assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("APPROVED_WITHOUT_DOCUMENT");
            settle();
            assertThat(incentiveOf(m.f().schoolId())).isNull();
        } finally {
            db.execute("alter table incentive drop constraint if exists ck_test_blocked");
            db.update("update event_publication set completion_date = now() where completion_date is null");
        }

        ReconcileJob.Result first = reconcile.runNow();
        assertThat(first.earned()).isGreaterThanOrEqualTo(1);
        assertThat(incentiveRows(m.f().schoolId())).isEqualTo(1);
        ReconcileJob.Result second = reconcile.runNow();
        assertThat(second.earned()).isZero();
        assertThat(incentiveRows(m.f().schoolId())).isEqualTo(1);
    }
}
