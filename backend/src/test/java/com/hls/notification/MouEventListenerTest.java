package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.MouTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 FR-015 and FR-016: who is told when a MoU is submitted or decided. */
class MouEventListenerTest extends MouTestBase {

    @Test
    void aSubmissionTellsDirectorsAndTheZoneManagerButNeverAdminOrTheSubmitter() {
        Mou m = mou();
        Signed otherAdmin = signInAs(Role.ADMIN);
        UUID submitterAdmin = m.adminUserId();
        int director = notifications(m.director().userId(), "MOU_SUBMITTED");
        int manager = notifications(m.f().manager().signed().userId(), "MOU_SUBMITTED");
        int admins = notifications(otherAdmin.userId(), "MOU_SUBMITTED");

        submit(m.admin(), m.contractId());

        assertThat(notifications(m.director().userId(), "MOU_SUBMITTED")).isEqualTo(director + 1);
        assertThat(notifications(m.f().manager().signed().userId(), "MOU_SUBMITTED")).isEqualTo(manager + 1);
        assertThat(notifications(otherAdmin.userId(), "MOU_SUBMITTED")).isEqualTo(admins);
        assertThat(notifications(submitterAdmin, "MOU_SUBMITTED")).isZero();
    }

    @Test
    void aDirectorWhoSubmitsIsNotToldOfTheirOwnSubmission() {
        Mou m = mou();
        int before = notifications(m.director().userId(), "MOU_SUBMITTED");

        submit(tokenOf(m.director()), m.contractId());

        assertThat(notifications(m.director().userId(), "MOU_SUBMITTED")).isEqualTo(before);
    }

    @Test
    void aDecisionTellsTheSubmitterAndEveryAdminButNotTheDecider() {
        Mou m = mou();
        Signed otherAdmin = signInAs(Role.ADMIN);
        UUID submitterAdmin = m.adminUserId();
        submit(m.admin(), m.contractId());
        int submitterBefore = notifications(submitterAdmin, "MOU_DECIDED");
        int adminBefore = notifications(otherAdmin.userId(), "MOU_DECIDED");
        int directorBefore = notifications(m.director().userId(), "MOU_DECIDED");

        reject(tokenOf(m.director()), m.contractId(), "Rate does not match");

        assertThat(notifications(submitterAdmin, "MOU_DECIDED")).isEqualTo(submitterBefore + 1);
        assertThat(notifications(otherAdmin.userId(), "MOU_DECIDED")).isEqualTo(adminBefore + 1);
        assertThat(notifications(m.director().userId(), "MOU_DECIDED")).isEqualTo(directorBefore);
        String message = jdbc.queryForObject(
                "select message from notification where recipient_user_id = ? and type = 'MOU_DECIDED' order by created_at desc limit 1",
                String.class,
                submitterAdmin);
        assertThat(message).contains("Rate does not match");
    }

    @Test
    void aDecisionAfterAnAmendmentSaysTermsWereCorrected() {
        Mou m = mou();
        UUID submitterAdmin = m.adminUserId();
        submit(m.admin(), m.contractId());
        patchTerms(m.managerToken(), m.contractId(), java.util.Map.of("version", versionOf(m.admin(), m.contractId()), "rate", "16000"));

        approve(m.managerToken(), m.contractId());

        String message = jdbc.queryForObject(
                "select message from notification where recipient_user_id = ? and type = 'MOU_DECIDED' order by created_at desc limit 1",
                String.class,
                submitterAdmin);
        assertThat(message).contains("approved").contains("corrected");
    }

    @Test
    void aSchoolWithoutAZoneManagerNotifiesDirectorsOnly() {
        String admin = signInAs(Role.ADMIN).token();
        Signed dir = director();
        // a School whose Manager is removed after the MoU is recorded
        Fixture f = fixture(admin);
        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 1, "1000")));
        java.util.Map<String, Object> clear = new java.util.HashMap<>();
        clear.put("managerId", null);
        put("/api/v1/schools/" + f.schoolId() + "/manager", admin, clear);
        int manager = notifications(f.manager().signed().userId(), "MOU_SUBMITTED");
        int director = notifications(dir.userId(), "MOU_SUBMITTED");

        submit(admin, contract);

        assertThat(notifications(dir.userId(), "MOU_SUBMITTED")).isEqualTo(director + 1);
        assertThat(notifications(f.manager().signed().userId(), "MOU_SUBMITTED")).isEqualTo(manager);
    }
}
