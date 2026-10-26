package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 031 US3: the pending list of approvers, oldest first, limited to the caller scope. */
class MouPendingListApiTest extends MouTestBase {

    private List<Map<String, Object>> pending(String token) {
        Resp resp = get(BASE + "/approvals/pending?size=100", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return content(resp);
    }

    private boolean contains(List<Map<String, Object>> rows, UUID contract) {
        return rows.stream().anyMatch(r -> contract.toString().equals(r.get("contractId")));
    }

    @Test
    void onlyPendingMoUsInScopeAreListedOldestFirstWithTheirFacts() {
        Mou first = mou();
        upload(first.admin(), first.contractId(), "a.pdf", PDF);
        submit(first.admin(), first.contractId());
        Mou second = mou();
        submit(second.admin(), second.contractId());
        Mou draft = mou();
        String dir = tokenOf(first.director());

        List<Map<String, Object>> rows = pending(dir);

        assertThat(contains(rows, first.contractId())).isTrue();
        assertThat(contains(rows, second.contractId())).isTrue();
        assertThat(contains(rows, draft.contractId())).isFalse();
        int a = indexOf(rows, first.contractId());
        int b = indexOf(rows, second.contractId());
        assertThat(a).isLessThan(b);
        assertThat(rows.get(a)).containsEntry("documentState", "ATTACHED").containsEntry("round", 1).containsEntry("teacherCount", 2);
        assertThat(rows.get(b)).containsEntry("documentState", "NONE");
        assertThat(rows.get(a).get("schoolName")).isNotNull();
        assertThat(rows.get(a).get("zoneManagerName")).isNotNull();
        assertThat(rows.get(a).get("submittedBy")).isNotNull();
    }

    private static int indexOf(List<Map<String, Object>> rows, UUID contract) {
        for (int i = 0; i < rows.size(); i++) {
            if (contract.toString().equals(rows.get(i).get("contractId"))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aManagerSeesOnlyTheirOwnSchoolsAndADecidedOneLeavesTheList() {
        Mou a = mou();
        Mou b = mou();
        submit(a.admin(), a.contractId());
        submit(b.admin(), b.contractId());

        assertThat(contains(pending(a.managerToken()), a.contractId())).isTrue();
        assertThat(contains(pending(a.managerToken()), b.contractId())).isFalse();
        assertThat(contains(pending(b.managerToken()), a.contractId())).isFalse();

        approve(a.managerToken(), a.contractId());
        assertThat(contains(pending(a.managerToken()), a.contractId())).isFalse();
    }

    @Test
    void nobodyWithoutApproveGetsTheList() {
        Mou m = mou();
        for (Role role : new Role[] {Role.ADMIN, Role.TEACHER, Role.SYSTEM}) {
            assertThat(get(BASE + "/approvals/pending", signInAs(role).token()).status()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(BASE + "/approvals/pending", m.managerToken()).status()).isEqualTo(200);
    }

    @Test
    void aSchoolWithoutAZoneManagerIsVisibleToTheDirectorOnly() {
        String admin = signInAs(Role.ADMIN).token();
        Signed dir = director();
        Fixture f = fixture(admin);
        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 1, "1000")));
        java.util.Map<String, Object> clear = new java.util.HashMap<>();
        clear.put("managerId", null);
        put("/api/v1/schools/" + f.schoolId() + "/manager", admin, clear);
        submit(admin, contract);

        assertThat(contains(pending(tokenOf(dir)), contract)).isTrue();
        assertThat(contains(pending(f.manager().token()), contract)).isFalse();
    }

    @Test
    void aChangedZoneManagerMovesVisibilityWithNoOtherChange() {
        String admin = signInAs(Role.ADMIN).token();
        Signed dir = director();
        Fixture f = fixture(admin);
        UUID contract = contractId(createContract(admin, f.schoolId(), sameSalaryBody(f, dir.userId(), 1, "1000")));
        submit(admin, contract);
        ManagerCtx outsider = fixture(admin).manager();
        ManagerCtx replacement = newManager(admin, f.zoneId());
        assertThat(contains(pending(outsider.token()), contract)).isFalse();

        assignSchoolManager(admin, f.schoolId(), replacement.managerId());

        assertThat(contains(pending(replacement.token()), contract)).isTrue();
        assertThat(contains(pending(outsider.token()), contract)).isFalse();
        assertThat(approve(replacement.token(), contract).status()).isEqualTo(200);
    }
}
