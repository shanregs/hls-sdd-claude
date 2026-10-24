package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 015 SC-004 (FR-015): nobody approves, rejects or reverses a claim they raised, whatever roles they hold. */
class SelfDecisionTest extends ExpensesTestBase {

    @Test
    void anAdminCannotDecideOrReverseTheirOwnClaimButADirectorCan() {
        Signed admin = admin();
        Signed director = director();
        UUID mine = newSubmittedClaim(admin.token(), "410.00");

        assertThat(approve(admin.token(), mine).status()).isEqualTo(403);
        assertThat(reject(admin.token(), mine, "No").status()).isEqualTo(403);
        assertThat(approve(admin.token(), mine).body()).contains("You raised this claim");
        assertThat(statusOf(admin.token(), mine)).isEqualTo("SUBMITTED");
        assertThat(allowedActions(get(MINE + "/" + mine, admin.token()))).containsExactly("WITHDRAW");

        assertThat(approve(director.token(), mine).status()).isEqualTo(200);
        assertThat(reverse(admin.token(), mine, "Changed my mind").status()).isEqualTo(403);
        assertThat(reviewStatusOf(director.token(), mine)).isEqualTo("APPROVED");
        assertThat(reverse(director.token(), mine, "Needs another look").status()).isEqualTo(200);
        assertThat(reviewStatusOf(director.token(), mine)).isEqualTo("SUBMITTED");
    }

    @Test
    void aDirectorsClaimIsDecidedByAnAdmin() {
        Signed admin = admin();
        Signed director = director();
        UUID theirs = newSubmittedClaim(director.token(), "99.00");

        assertThat(approve(director.token(), theirs).status()).isEqualTo(403);
        assertThat(reject(director.token(), theirs, "No").status()).isEqualTo(403);
        assertThat(approve(admin.token(), theirs).status()).isEqualTo(200);
        assertThat(reverse(director.token(), theirs, "Own claim").status()).isEqualTo(403);
    }

    @Test
    void aUserHoldingBothApproverRolesCannotDecideAClaimTheyRaised() {
        Signed both = signInAs(Role.ADMIN, Role.DIRECTOR);
        Signed other = admin();
        UUID mine = newSubmittedClaim(both.token(), "250.00");

        assertThat(approve(both.token(), mine).status()).isEqualTo(403);
        assertThat(reject(both.token(), mine, "No").status()).isEqualTo(403);
        assertThat(post(REVIEW + "/" + mine + "/reverse", both.token(), Map.of("version", 0, "reason", "x")).status()).isEqualTo(403);
        assertThat(allowedActions(get(REVIEW + "/" + mine, both.token()))).doesNotContain("APPROVE", "REJECT", "REVERSE");

        assertThat(approve(other.token(), mine).status()).isEqualTo(200);
        assertThat(reverse(both.token(), mine, "x").status()).isEqualTo(403);
        assertThat(allowedActions(get(REVIEW + "/" + mine, both.token()))).doesNotContain("REVERSE");
    }

    @Test
    void anAdminMayRecordOwnApprovedClaimPaidButNeverDecideIt() {
        Signed admin = admin();
        Signed director = director();
        UUID mine = newSubmittedClaim(admin.token(), "60.00");
        assertThat(approve(director.token(), mine).status()).isEqualTo(200);
        assertThat(allowedActions(get(REVIEW + "/" + mine, admin.token()))).containsExactly("RECORD_PAID");
    }
}
