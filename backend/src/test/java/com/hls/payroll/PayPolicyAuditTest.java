package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules;
import com.hls.support.PayrollTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 013a FR-014 (T023): each changed field of a policy version writes an entry with the actor and both values. */
class PayPolicyAuditTest extends PayrollTestBase {

    @Autowired
    private PayRules payRules;

    @Test
    void eachChangedFieldWritesOneEntryAgainstTheVersionInEffectBefore() {
        Signed signed = signInAs(Role.ADMIN);
        String admin = signed.token();

        // whatever other tests recorded earlier in the shared database, the entry compares with what was in effect
        LocalDate date = LocalDate.of(2096, 1, 1);
        PayPolicyView before = payRules.policyOn(date);
        String newRounding = before.rounding() == PayPolicyView.RoundingRule.UP ? "DOWN" : "UP";
        Resp created = recordPolicy(admin, date, "0.123", newRounding);
        assertThat(created.status()).as(created.body()).isEqualTo(201);

        List<Map<String, Object>> entries = changeEntries(admin, "PAY_POLICY", created.id(), 2);
        assertThat(entries).hasSize(2).allSatisfy(e -> {
            assertThat(e).containsEntry("entityId", created.id().toString());
            assertThat(e).containsEntry("actorUserId", signed.userId().toString());
        });
        assertThat(entries)
                .extracting(e -> e.get("field") + ": " + e.get("beforeValue") + " -> " + e.get("afterValue"))
                .containsExactlyInAnyOrder(
                        "halfDayFraction: " + before.halfDayFraction().setScale(3).toPlainString() + " -> 0.123",
                        "rounding: " + before.rounding() + " -> " + newRounding);
    }

    @Test
    void aVersionThatChangesNothingStillLeavesOneRecordOfWhoDidIt() {
        Signed signed = signInAs(Role.ADMIN);
        LocalDate date = LocalDate.of(2097, 1, 1);
        PayPolicyView same = payRules.policyOn(date);
        Resp created = recordPolicy(
                signed.token(), date, same.halfDayFraction().setScale(3).toPlainString(), same.rounding().name());
        assertThat(created.status()).as(created.body()).isEqualTo(201);

        List<Map<String, Object>> entries = changeEntries(signed.token(), "PAY_POLICY", created.id(), 1);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("field", "created").containsEntry("actorUserId", signed.userId().toString());
    }

    @Test
    void aRefusedVersionWritesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        int before = totalEntries(admin);
        assertThat(recordPolicy(admin, "2098-01-01", "1.5", "UP").status()).isEqualTo(400);
        assertThat(totalEntries(admin)).isEqualTo(before);
    }

    private int totalEntries(String admin) {
        Resp resp = get("/api/v1/audit/change-history?entityType=PAY_POLICY&size=100", admin);
        return (int) total(resp);
    }
}
