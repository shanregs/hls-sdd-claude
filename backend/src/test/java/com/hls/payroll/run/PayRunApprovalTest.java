package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T028: submit, return and approve, and the separation of the person who submits from the one who approves. */
class PayRunApprovalTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private static String status(Map<String, Object> run) {
        return (String) run.get("status");
    }

    private static String submitterId(Map<String, Object> run) {
        @SuppressWarnings("unchecked")
        Map<String, Object> by = (Map<String, Object>) run.get("submittedBy");
        return by == null ? null : (String) by.get("id");
    }

    @Test
    void submitMovesTheDraftToAwaitingApprovalAndLocksItAgainstChange() {
        Signed admin = signInAs(Role.ADMIN);
        Draft draft = readyDraft(admin.token());

        Resp submitted = submitRun(admin.token(), draft.run());

        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        assertThat(status(submitted.map())).isEqualTo("AWAITING_APPROVAL");
        assertThat(submitterId(submitted.map())).isEqualTo(admin.userId().toString());
        // nothing may change while it waits: not computed again, not cancelled, not submitted twice
        assertThat(post(RUNS + "/" + draft.run() + "/recompute", admin.token(), Map.of()).status()).isEqualTo(409);
        assertThat(post(RUNS + "/" + draft.run() + "/cancel", admin.token(), Map.of("reason", "x")).status())
                .isEqualTo(409);
        assertThat(submitRun(admin.token(), draft.run()).status()).isEqualTo(409);
    }

    @Test
    void submitWithANegativeNetLineIsRefusedNamingTheLine() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        String name = (String) lineOf(admin, draft.run(), draft.teacher()).get("name");
        // a deduction larger than the earned pay; the line is reported, never clamped to zero
        jdbc.update(
                "update payroll_run_line set deduction = earned + 100, net = -100 where run_id = ? and person_id = ?",
                draft.run(),
                draft.teacher());

        Resp refused = submitRun(admin, draft.run());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains(name).contains("below zero").contains("-₹100.00");
        assertThat(status(runOf(admin, draft.run()))).isEqualTo("DRAFT");
    }

    @Test
    void submitWhileComputingOrBeforeItWasComputedIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        jdbc.update("update payroll_run set compute_state = 'RUNNING', heartbeat_at = now() where id = ?", draft.run());
        assertThat(submitRun(admin, draft.run()).body()).contains("being computed");

        jdbc.update("update payroll_run set compute_state = 'FAILED' where id = ?", draft.run());
        Resp failed = submitRun(admin, draft.run());

        assertThat(failed.status()).isEqualTo(409);
        assertThat(failed.body()).contains("not been computed");
    }

    @Test
    void returnNeedsAReasonGoesBackToDraftAndKeepsTheReasonInTheHistory() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);
        submitRun(admin, draft.run());

        assertThat(returnRun(director, draft.run(), "").status()).isEqualTo(400);
        assertThat(returnRun(director, draft.run(), "x".repeat(501)).status()).isEqualTo(400);
        Resp returned = returnRun(director, draft.run(), "Check the deduction");

        assertThat(returned.status()).as(returned.body()).isEqualTo(200);
        assertThat(status(returned.map())).isEqualTo("DRAFT");
        assertThat(get(RUNS + "/" + draft.run(), admin).body()).contains("RETURNED").contains("Check the deduction");
        // it is a draft again: the Admin can compute it and submit it once more
        assertThat(post(RUNS + "/" + draft.run() + "/recompute", admin, Map.of()).status()).isEqualTo(202);
        awaitComputed(admin, draft.run());
        assertThat(submitRun(admin, draft.run()).status()).isEqualTo(200);
    }

    @Test
    void returnOfARunThatIsNotAwaitingApprovalIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);

        assertThat(returnRun(director, draft.run(), "no").status()).isEqualTo(409);
    }

    @Test
    void approveRecordsWhoAndWhenIssuesPayslipsAndRecordsWhoIsPaid() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Draft draft = readyDraft(admin.token());
        submitRun(admin.token(), draft.run());

        Resp approved = approveRun(director.token(), draft.run());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        Map<String, Object> run = approved.map();
        assertThat(status(run)).isEqualTo("APPROVED");
        @SuppressWarnings("unchecked")
        Map<String, Object> by = (Map<String, Object>) run.get("approvedBy");
        assertThat(by.get("id")).isEqualTo(director.userId().toString());
        assertThat(run.get("approvedAt")).isNotNull();
        int paid = ((Number) run.get("payableCount")).intValue();
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip where run_id = ?", Long.class, draft.run()))
                .isEqualTo((long) paid);
        assertThat(jdbc.queryForObject("select count(*) from payroll_paid_month where run_id = ?", Long.class, draft.run()))
                .isEqualTo((long) paid);
        assertThat(jdbc.queryForObject(
                        "select count(*) from payroll_paid_month where run_id = ? and person_id = ?",
                        Long.class,
                        draft.run(),
                        draft.teacher()))
                .isEqualTo(1L);
        assertThat(get(RUNS + "/" + draft.run(), admin.token()).body()).contains("APPROVED");
    }

    @Test
    void aUserHoldingBothRolesWhoSubmittedCannotApproveTheirOwnSubmission() {
        Signed both = signInAs(Role.ADMIN, Role.DIRECTOR);
        Draft draft = readyDraft(both.token());
        assertThat(submitRun(both.token(), draft.run()).status()).isEqualTo(200);

        Resp refused = approveRun(both.token(), draft.run());

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body()).contains("someone else must approve");
        assertThat(status(runOf(both.token(), draft.run()))).isEqualTo("AWAITING_APPROVAL");
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip where run_id = ?", Long.class, draft.run()))
                .isZero();
    }

    @Test
    void theNewSubmitterIsRecordedAfterAReturn() {
        Signed first = signInAs(Role.ADMIN);
        Signed second = signInAs(Role.ADMIN);
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(first.token());
        submitRun(first.token(), draft.run());
        returnRun(director, draft.run(), "Look again");

        Resp again = submitRun(second.token(), draft.run());

        assertThat(submitterId(again.map())).isEqualTo(second.userId().toString());
        assertThat(approveRun(director, draft.run()).status()).isEqualTo(200);
    }

    @Test
    void approveOfARunNotAwaitingApprovalIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);

        assertThat(approveRun(director, draft.run()).status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip where run_id = ?", Long.class, draft.run()))
                .isZero();
    }

    @Test
    void everyAttemptToChangeAnApprovedRunIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        String director = submitAndApprove(admin, draft.run());
        List<Map<String, Object>> before = allLines(admin, draft.run());

        assertThat(post(RUNS + "/" + draft.run() + "/recompute", admin, Map.of()).status()).isEqualTo(409);
        assertThat(post(RUNS + "/" + draft.run() + "/cancel", admin, Map.of("reason", "x")).status()).isEqualTo(409);
        assertThat(submitRun(admin, draft.run()).status()).isEqualTo(409);
        assertThat(returnRun(director, draft.run(), "too late").status()).isEqualTo(409);
        assertThat(approveRun(director, draft.run()).status()).isEqualTo(409);
        assertThat(delete(RUNS + "/" + draft.run(), admin).status()).isIn(404, 405);
        assertThat(allLines(admin, draft.run())).isEqualTo(before);
        assertThat(status(runOf(admin, draft.run()))).isEqualTo("APPROVED");
    }

    @Test
    void anAdminCannotApproveAndADirectorCannotSubmit() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);

        assertThat(submitRun(director, draft.run()).status()).isEqualTo(403);
        submitRun(admin, draft.run());
        assertThat(approveRun(admin, draft.run()).status()).isEqualTo(403);
        assertThat(returnRun(admin, draft.run(), "no").status()).isEqualTo(403);
        assertThat(status(runOf(admin, draft.run()))).isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    void anUnknownRunAnswers404ForTheNewEndpoints() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID unknown = UUID.randomUUID();

        assertThat(submitRun(admin, unknown).status()).isEqualTo(404);
        assertThat(returnRun(director, unknown, "x").status()).isEqualTo(404);
        assertThat(approveRun(director, unknown).status()).isEqualTo(404);
    }
}
