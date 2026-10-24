package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 031 US3: approval and document columns and filters on the School Contracts list, and the School page summary. */
class MouListApiTest extends MouTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Map<String, Object> rowOf(String token, UUID schoolId, String query) {
        return content(get(BASE + "?size=100" + query, token)).stream()
                .filter(r -> schoolId.toString().equals(r.get("schoolId")))
                .findFirst()
                .orElse(null);
    }

    @Test
    void eachStateShowsInTheListWithItsDocumentStateAndPendingSince() {
        Mou draft = mou();
        Mou pending = mou();
        upload(pending.admin(), pending.contractId(), "a.pdf", PDF);
        submit(pending.admin(), pending.contractId());
        Mou rejected = mou();
        submit(rejected.admin(), rejected.contractId());
        reject(rejected.managerToken(), rejected.contractId(), "no");
        Mou approved = mou();
        upload(approved.admin(), approved.contractId(), "a.pdf", PDF);
        submit(approved.admin(), approved.contractId());
        approve(tokenOf(approved.director()), approved.contractId());
        Mou legacy = mou();
        makeLegacy(legacy.contractId());
        String admin = draft.admin();

        assertThat(rowOf(admin, draft.f().schoolId(), "")).containsEntry("approvalState", "DRAFT").containsEntry("documentState", "NONE");
        assertThat(rowOf(admin, pending.f().schoolId(), ""))
                .containsEntry("approvalState", "PENDING")
                .containsEntry("documentState", "ATTACHED")
                .doesNotContainEntry("pendingSince", null);
        assertThat(rowOf(admin, rejected.f().schoolId(), "")).containsEntry("approvalState", "REJECTED");
        assertThat(rowOf(admin, approved.f().schoolId(), ""))
                .containsEntry("approvalState", "APPROVED")
                .containsEntry("documentState", "ATTACHED");
        assertThat(rowOf(admin, legacy.f().schoolId(), "")).containsEntry("approvalState", "APPROVED_BEFORE_APPROVAL_STEP");
        assertThat(rowOf(admin, draft.f().schoolId(), "")).containsEntry("pendingSince", null);
    }

    @Test
    void theApprovalAndDocumentFiltersWorkAloneAndWithStatus() {
        Mou pending = mou();
        submit(pending.admin(), pending.contractId());
        Mou draft = mou();
        String admin = pending.admin();

        assertThat(rowOf(admin, pending.f().schoolId(), "&approval=PENDING")).isNotNull();
        assertThat(rowOf(admin, draft.f().schoolId(), "&approval=PENDING")).isNull();
        assertThat(rowOf(admin, draft.f().schoolId(), "&document=NONE")).isNotNull();
        upload(draft.admin(), draft.contractId(), "a.pdf", PDF);
        assertThat(rowOf(admin, draft.f().schoolId(), "&document=NONE")).isNull();
        assertThat(rowOf(admin, draft.f().schoolId(), "&document=ATTACHED&approval=DRAFT")).isNotNull();
        assertThat(rowOf(admin, draft.f().schoolId(), "&document=ATTACHED&approval=DRAFT&status=ACTIVE")).isNotNull();
        assertThat(rowOf(admin, draft.f().schoolId(), "&approval=DRAFT&status=CANCELLED")).isNull();
    }

    @Test
    void aManagersListIsLimitedToTheirScope() {
        Mou mine = mou();
        Mou other = mou();
        String manager = mine.managerToken();

        assertThat(rowOf(manager, mine.f().schoolId(), "&approval=DRAFT")).isNotNull();
        assertThat(rowOf(manager, other.f().schoolId(), "&approval=DRAFT")).isNull();
    }

    @Test
    void theSchoolPageCarriesTheApprovalSummaryAndActionsOfEachContract() {
        Mou m = mou();
        upload(m.admin(), m.contractId(), "a.pdf", PDF);
        submit(m.admin(), m.contractId());

        Map<String, Object> asManager = contractsOf(m.managerToken(), m.f().schoolId()).get(0);
        Map<String, Object> asAdmin = contractsOf(m.admin(), m.f().schoolId()).get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> managerApproval = (Map<String, Object>) asManager.get("approval");
        @SuppressWarnings("unchecked")
        Map<String, Object> adminApproval = (Map<String, Object>) asAdmin.get("approval");
        assertThat(managerApproval).containsEntry("state", "PENDING").doesNotContainKeys("history", "documents");
        assertThat((List<Object>) managerApproval.get("actions")).containsExactly("APPROVE", "REJECT", "AMEND_TERMS");
        assertThat((List<Object>) adminApproval.get("actions")).containsExactly("UPLOAD", "REMOVE_DOCUMENT");
        assertThat(adminApproval.get("document").toString()).contains("ATTACHED");
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheNumberOfSchools() {
        String admin = signInAs(Role.ADMIN).token();
        Signed dir = director();
        UUID zone = zone(admin);
        UUID place = place(admin, zone);
        ManagerCtx manager = newManager(admin, zone);
        addSchoolWithMou(admin, place, manager, dir);
        SessionFactory sessions = entityManagerFactory.unwrap(SessionFactory.class);
        sessions.getStatistics().setStatisticsEnabled(true);
        get(BASE + "?size=100", manager.token());

        settle();
        sessions.getStatistics().clear();
        get(BASE + "?size=100", manager.token());
        long one = sessions.getStatistics().getPrepareStatementCount();

        for (int i = 0; i < 4; i++) {
            addSchoolWithMou(admin, place, manager, dir);
        }
        settle();
        sessions.getStatistics().clear();
        Resp list = get(BASE + "?size=100", manager.token());
        long five = sessions.getStatistics().getPrepareStatementCount();

        sessions.getStatistics().setStatisticsEnabled(false);

        assertThat(content(list)).hasSize(5);
        assertThat(five).isEqualTo(one);
    }

    /** Lets the asynchronous audit writers of the set-up calls finish so they are not counted. */
    private static void settle() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void addSchoolWithMou(String admin, UUID place, ManagerCtx manager, Signed dir) {
        UUID school = school(admin, place);
        assignSchoolManager(admin, school, manager.managerId());
        Fixture f = fixtureOf(null, school, manager);
        contractId(createContract(admin, school, sameSalaryBody(f, dir.userId(), 1, "1000")));
    }
}
