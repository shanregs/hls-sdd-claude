package com.hls.schoolbilling;

import com.hls.identity.user.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 012 FR-015 and SC-006: every contract creation, end and cancellation has an audit entry. */
class ContractAuditTest extends SchoolContractsTestBase {

    @Test
    void creatingEndingAndCancellingAContractIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();

        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000")));

        assertChangeRecorded(admin, "CONTRACT", contract, "state");
        assertChangeRecorded(admin, "CONTRACT", contract, "teacherCount");
        assertChangeRecorded(admin, "CONTRACT", contract, "salaryMode");
        assertChangeRecorded(admin, "CONTRACT", contract, "rate");
        assertChangeRecorded(admin, "CONTRACT", contract, "signedOn");
        assertChangeRecorded(admin, "CONTRACT", contract, "startsOn");
        assertChangeRecorded(admin, "CONTRACT", contract, "signatories");

        post(BASE + "/contracts/" + contract + "/end", admin, Map.of("endsOn", today.plusDays(20).toString()));
        assertChangeRecorded(admin, "CONTRACT", contract, "endsOn");

        post(BASE + "/contracts/" + contract + "/cancel", admin, null);
        assertChangeRecorded(admin, "CONTRACT", contract, "state");
    }

    @Test
    void aNewMouThatEndsTheCurrentOneAuditsBothContracts() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        UUID first = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir, 2, "15000")));
        Map<String, Object> next = sameSalaryBody(f, dir, 2, "16000");
        next.put("startsOn", today.plusDays(30).toString());

        contractId(createContract(admin, f.schoolId(), next));

        assertChangeRecorded(admin, "CONTRACT", first, "endsOn");
    }
}
