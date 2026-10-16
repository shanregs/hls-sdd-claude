package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T034: who hears of a submission, an approval and a return, and what the message may say. */
class PayrollNotificationTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private List<Map<String, Object>> rows(UUID userId, String type) {
        return jdbc.queryForList(
                "select title, message, link from notification where recipient_user_id = ? and type = ? order by created_at",
                userId,
                type);
    }

    @Test
    void submittingTellsTheApproversButNotTheSubmitterAndNeverShowsAnAmount() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Draft draft = readyDraft(admin.token());

        submitRun(admin.token(), draft.run());

        List<Map<String, Object>> told = rows(director.userId(), "PAYROLL_RUN_SUBMITTED");
        assertThat(told).hasSize(1);
        assertThat((String) told.get(0).get("message")).contains(monthName()).contains("awaiting approval").doesNotContain("₹");
        assertThat((String) told.get(0).get("link")).isEqualTo("/operations/payroll/" + draft.run());
        assertThat(rows(admin.userId(), "PAYROLL_RUN_SUBMITTED")).isEmpty();
    }

    @Test
    void approvingTellsTheSubmitter() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Draft draft = readyDraft(admin.token());
        submitRun(admin.token(), draft.run());

        approveRun(director.token(), draft.run());

        List<Map<String, Object>> told = rows(admin.userId(), "PAYROLL_RUN_APPROVED");
        assertThat(told).hasSize(1);
        assertThat((String) told.get(0).get("message")).contains(monthName()).contains("approved").doesNotContain("₹");
        assertThat(rows(director.userId(), "PAYROLL_RUN_APPROVED")).isEmpty();
    }

    @Test
    void returningTellsTheSubmitterWithTheReason() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        Draft draft = readyDraft(admin.token());
        submitRun(admin.token(), draft.run());

        returnRun(director.token(), draft.run(), "Check the deduction");

        List<Map<String, Object>> told = rows(admin.userId(), "PAYROLL_RUN_RETURNED");
        assertThat(told).hasSize(1);
        assertThat((String) told.get(0).get("message"))
                .contains(monthName())
                .contains("returned to draft")
                .contains("Check the deduction")
                .doesNotContain("₹");
    }

    @Test
    void aRefusedApprovalTellsNobody() {
        Signed both = signInAs(Role.ADMIN, Role.DIRECTOR);
        Draft draft = readyDraft(both.token());
        submitRun(both.token(), draft.run());

        assertThat(approveRun(both.token(), draft.run()).status()).isEqualTo(403);

        assertThat(rows(both.userId(), "PAYROLL_RUN_APPROVED")).isEmpty();
    }

    private String monthName() {
        return com.hls.payroll.run.internal.RunInput.monthName(month);
    }
}
