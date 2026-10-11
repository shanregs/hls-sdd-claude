package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a FR-014 (T015): every recorded salary writes one entry with the actor and the prior and new amount. */
class SalaryStructureAuditTest extends PayrollTestBase {

    @Test
    void eachRecordedSalaryWritesOneEntryWithTheActorAndThePriorAmount() {
        Signed signed = signInAs(Role.ADMIN);
        String admin = signed.token();
        UUID designation = designation(admin, "MANAGER");
        LocalDate first = firstOfMonth();

        recordSalaryOk(admin, designation, "30000", first);
        recordSalaryOk(admin, designation, "32000", first.plusMonths(2));
        // the prior amount is the one in effect on the new row's own date before it: 30000 for both later rows
        recordSalaryOk(admin, designation, "31000", first);

        List<Map<String, Object>> entries = changeEntries(admin, "DESIGNATION_SALARY", designation, 3);
        assertThat(entries).hasSize(3).allSatisfy(e -> {
            assertThat(e).containsEntry("field", "amount").containsEntry("entityId", designation.toString());
            assertThat(e).containsEntry("actorUserId", signed.userId().toString());
        });
        assertThat(entries)
                .extracting(e -> e.get("beforeValue") + " -> " + e.get("afterValue"))
                .containsExactlyInAnyOrder("null -> 30000.00", "30000.00 -> 32000.00", "30000.00 -> 31000.00");
    }

    @Test
    void aRefusedRequestWritesNoEntry() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        assertThat(recordSalary(admin, designation, "0", firstOfMonth(), null).status()).isEqualTo(400);
        assertThat(recordSalary(admin, designation, "100", firstOfMonth().minusDays(1), null).status()).isEqualTo(400);
        assertThat(changeCount(admin, "DESIGNATION_SALARY", designation)).isZero();
    }
}
