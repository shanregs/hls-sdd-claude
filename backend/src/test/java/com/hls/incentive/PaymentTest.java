package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 030 US3 (T022, SC-003, SC-007): an approved incentive is paid once, through the Manager's pay run. */
class PaymentTest extends IncentiveTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private com.hls.incentive.internal.ReconcileJob reconcile;

    private String admin;

    @BeforeEach
    void clean() {
        resetRuns();
        admin = adminToken();
    }

    /** A pending incentive earned by the School's Zone Manager, who is a paid Manager with a locked month. */
    private record Case(Mou mou, UUID id, UUID managerId) {}

    private Case pendingForPaidManager() {
        Mou m = mou();
        makePayable(m.admin(), m.f().manager(), "30000");
        Map<String, Object> row = earnFor(m);
        return new Case(m, idOf(row), m.f().manager().managerId());
    }

    private Map<String, Object> earnFor(Mou m) {
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        approveMou(m);
        return awaitIncentive(m.f().schoolId());
    }

    private void approveIncentive(Case c) {
        long version = ((Number) ((Map<?, ?>) get(API + "/" + c.id(), c.mou().director().token()).map().get("incentive")).get("version")).longValue();
        Resp resp = post(API + "/" + c.id() + "/approve", c.mou().director().token(), Map.of("version", version));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    private String payrollStatus(Case c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> incentive = (Map<String, Object>) get(API + "/" + c.id(), c.mou().director().token()).map().get("incentive");
        return (String) incentive.get("payrollStatus");
    }

    private BigDecimal adjustments(UUID run, Case c) {
        Map<String, Object> line = lineOf(admin, run, c.managerId());
        assertThat(line).as("the Manager has a line in the run").isNotNull();
        Object value = line.get("adjustments");
        return new BigDecimal(value == null ? "0" : (String) value);
    }

    @Test
    void anApprovedIncentiveIsPaidOnceInTheNextRunAndThenCannotChange() {
        Case c = pendingForPaidManager();
        assertThat(payrollStatus(c)).isNull();
        approveIncentive(c);
        assertThat(payrollStatus(c)).isEqualTo("Waiting for the next pay run");

        UUID run = startAndCompute(admin);
        assertThat(adjustments(run, c)).isEqualByComparingTo("5000.00");
        assertThat(payrollStatus(c)).isEqualTo("In the draft pay run");

        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(payrollStatus(c)).isEqualTo("In a pay run awaiting approval");
        assertThat(approveRun(c.mou().director().token(), run).status()).isEqualTo(200);

        Map<String, Object> row = incentiveOf(c.mou().f().schoolId());
        assertThat(row.get("status")).isEqualTo("PAID_IN_RUN");
        assertThat(row.get("run_id")).isEqualTo(run);
        assertThat(((java.sql.Date) row.get("run_month")).toLocalDate()).isEqualTo(payMonth().atDay(1));
        assertThat(payrollStatus(c)).isEqualTo("Paid in a pay run");
        assertThat(eventKinds(c.id())).containsExactly("EARNED", "APPROVED", "OFFERED", "APPLIED");
        assertThat(db.queryForObject("select count(*) from payroll_adjustment where source_ref = ? and status = 'APPLIED'", Integer.class,
                        "INCENTIVE:" + c.id()))
                .isEqualTo(1);

        // it is final: no further decision, no second payment
        assertThat(post(API + "/" + c.id() + "/reject", c.mou().director().token(), Map.of("version", 9, "reason", "x")).status())
                .isEqualTo(409);
        // a repeat of the event or the daily job offers nothing more
        reconcile.runNow();
        assertThat(db.queryForObject("select count(*) from payroll_adjustment where source_ref = ?", Integer.class, "INCENTIVE:" + c.id()))
                .isEqualTo(1);
    }

    @Test
    void returnAndRecomputeKeepOneLine() {
        Case c = pendingForPaidManager();
        approveIncentive(c);
        UUID run = startAndCompute(admin);
        assertThat(adjustments(run, c)).isEqualByComparingTo("5000.00");

        recompute(admin, run);
        assertThat(adjustments(run, c)).isEqualByComparingTo("5000.00");

        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(returnRun(c.mou().director().token(), run, "Check the figures").status()).isEqualTo(200);
        recompute(admin, run);
        assertThat(adjustments(run, c)).isEqualByComparingTo("5000.00");
        assertThat(db.queryForObject("select count(*) from payroll_adjustment where source_ref = ?", Integer.class, "INCENTIVE:" + c.id()))
                .isEqualTo(1);
        assertThat(incentiveOf(c.mou().f().schoolId()).get("status")).isEqualTo("APPROVED");
    }

    @Test
    void aManagerWhoCannotBePaidKeepsTheIncentiveWaiting() {
        Mou m = mou();
        makePayable(m.admin(), m.f().manager(), null);
        Case c = new Case(m, idOf(earnFor(m)), m.f().manager().managerId());
        approveIncentive(c);

        UUID run = startAndCompute(admin);
        assertThat(lineOf(admin, run, c.managerId())).containsEntry("state", "CANNOT_PAY");

        assertThat(incentiveOf(m.f().schoolId()).get("status")).isEqualTo("APPROVED");
        assertThat(db.queryForObject("select status from payroll_adjustment where source_ref = ?", String.class, "INCENTIVE:" + c.id()))
                .isEqualTo("WAITING");
    }

    @Test
    void otherPeopleAreNotAffectedByAnIncentive() {
        Case withIncentive = pendingForPaidManager();
        Case other = pendingForPaidManager();
        approveIncentive(withIncentive);
        // the other Manager's incentive stays pending, so nothing is offered for them

        UUID run = startAndCompute(admin);

        assertThat(adjustments(run, withIncentive)).isEqualByComparingTo("5000.00");
        assertThat(adjustments(run, other)).isEqualByComparingTo("0.00");
        Map<String, Object> a = lineOf(admin, run, withIncentive.managerId());
        Map<String, Object> b = lineOf(admin, run, other.managerId());
        assertThat(new BigDecimal((String) a.get("net")).subtract(new BigDecimal((String) b.get("net"))))
                .isEqualByComparingTo("5000.00");
    }

    @Test
    void aRunApprovedBeforeTheIncentiveWasApprovedIsNotChanged() {
        Case c = pendingForPaidManager();
        UUID run = startAndCompute(admin);
        Map<String, Object> before = lineOf(admin, run, c.managerId());
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(approveRun(c.mou().director().token(), run).status()).isEqualTo(200);

        approveIncentive(c);

        Map<String, Object> after = lineOf(admin, run, c.managerId());
        assertThat(after.get("net")).isEqualTo(before.get("net"));
        assertThat(adjustments(run, c)).isEqualByComparingTo("0.00");
        assertThat(incentiveOf(c.mou().f().schoolId()).get("status")).isEqualTo("APPROVED");
    }
}
