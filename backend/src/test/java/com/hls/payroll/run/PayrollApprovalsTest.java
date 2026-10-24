package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 028 T043: the PAYROLL_RUN inbox kind lists runs awaiting approval for approvers only. */
class PayrollApprovalsTest extends PayrollRunTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox?kind=PAYROLL_RUN&size=100";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    @SuppressWarnings("unchecked")
    private static List<String> inboxIds(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @Test
    void aSubmittedRunIsInTheApproversInboxAndLeavesItWhenApproved() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);
        assertThat(submitRun(admin, draft.run()).status()).isEqualTo(200);

        Resp inbox = get(INBOX, director);
        assertThat(inbox.status()).as(inbox.body()).isEqualTo(200);
        assertThat(inboxIds(inbox)).contains(draft.run().toString());
        // the Admin holds no Approve grant on Payroll, so the kind is not theirs
        assertThat(get("/api/v1/dashboard/inbox", admin).body()).doesNotContain("PAYROLL_RUN");

        assertThat(approveRun(director, draft.run()).status()).isEqualTo(200);
        assertThat(inboxIds(get(INBOX, director))).doesNotContain(draft.run().toString());
    }
}
